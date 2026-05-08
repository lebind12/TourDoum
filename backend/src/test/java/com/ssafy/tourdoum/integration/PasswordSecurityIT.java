package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import com.ssafy.tourdoum.member.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-0011 BE-4 비밀번호 보안 통합 테스트 — Testcontainers(MySQL + Redis).
 *
 * <p>커버 범위:
 *
 * <ul>
 *   <li>BE-4.4 — 계정 5회 실패 → 423 LOCKED.
 *   <li>BE-4.3 — password 변경 → 기존 access token 401 (revocation epoch).
 *   <li>BE-4.5 — reset 토큰 1회용 (두 번째 사용 시 400).
 *   <li>BE-4.6 — legacy BCrypt 해시 시드 → 로그인 성공 후 Argon2id로 rehash.
 * </ul>
 *
 * <p>실행: {@code ./mvnw verify -Dtourdoum.it=true}. argon2 비용은
 * {@code -Dtourdoum.auth.argon2.memory-kib=16384}로 축소 가능.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfSystemProperty(named = "tourdoum.it", matches = "true")
class PasswordSecurityIT {

  @SuppressWarnings("resource")
  @Container
  static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("tourdoum")
          .withUsername("tourdoum")
          .withPassword("tourdoum");

  @SuppressWarnings("resource")
  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:mysql://"
                + mysql.getHost()
                + ":"
                + mysql.getMappedPort(3306)
                + "/tourdoum?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    // IT 비용 절감 — Argon2id 메모리 축소 (production 64MiB).
    registry.add("tourdoum.auth.argon2.memory-kib", () -> "16384");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MemberRepository memberRepository;

  @Test
  @DisplayName("BE-4.4 계정 5회 실패 → 6번째 시도 시 423 LOCKED")
  void account_lockout_after_five_failures() throws Exception {
    String email = "lock-" + System.nanoTime() + "@example.com";
    String password = "Str0ngPass!2026";
    signup(email, password, "lockuser");

    for (int i = 0; i < 5; i++) {
      mockMvc
          .perform(
              post("/api/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json("email", email, "password", "WrongPass!2026")))
          .andExpect(status().isUnauthorized());
    }
    // 6번째 — 정확한 비밀번호여도 잠금 적용.
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password)))
        .andExpect(status().isLocked());
  }

  @Test
  @DisplayName("BE-4.3 password change → 기존 access token revocation epoch로 401")
  void password_change_revokes_old_access() throws Exception {
    String email = "chg-" + System.nanoTime() + "@example.com";
    String password = "Str0ngPass!2026";
    String newPassword = "NewSecur3Pa$$word!";
    signup(email, password, "chguser");

    String access = login(email, password);
    // 토큰 발급 직후 epoch는 동일 초 — bump 후 1초 차이 보장.
    Thread.sleep(1100);

    // /api/me 정상 200
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());

    // password 변경
    mockMvc
        .perform(
            post("/api/auth/password")
                .with(csrf())
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("currentPassword", password, "newPassword", newPassword)))
        .andExpect(status().isNoContent());

    // 같은 access는 epoch < 새 epoch → 401
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isUnauthorized());

    // 새 비밀번호로 재로그인 가능
    String newAccess = login(email, newPassword);
    assertThat(newAccess).isNotBlank();
  }

  @Test
  @DisplayName("BE-4.5 reset 토큰 1회용 — 두 번째 complete는 400")
  void reset_token_single_use() throws Exception {
    String email = "rst-" + System.nanoTime() + "@example.com";
    signup(email, "Str0ngPass!2026", "rstuser");

    MvcResult initiate =
        mockMvc
            .perform(
                post("/api/auth/password-reset/initiate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", email)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.debugToken").exists())
            .andReturn();
    String token =
        objectMapper.readTree(initiate.getResponse().getContentAsString()).get("debugToken").asText();

    // 1회 — 정상 (204)
    mockMvc
        .perform(
            post("/api/auth/password-reset/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", token, "newPassword", "Reset3dPa$$w0rd!")))
        .andExpect(status().isNoContent());

    // 2회 — 동일 토큰 재사용 → 400
    mockMvc
        .perform(
            post("/api/auth/password-reset/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("token", token, "newPassword", "Reset3dPa$$w0rd!2")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("BE-4.6 legacy BCrypt 해시 시드 → 로그인 성공 후 Argon2id로 rehash")
  void bcrypt_legacy_rehashes_to_argon2_on_login() throws Exception {
    String email = "rehash-" + System.nanoTime() + "@example.com";
    String password = "Str0ngPass!2026";
    String legacyHash = "{bcrypt}" + new BCryptPasswordEncoder(10).encode(password);
    Member m =
        Member.builder()
            .email(email)
            .password(legacyHash)
            .nickname("rehash" + System.nanoTime())
            .role(MemberRole.ROLE_USER)
            .build();
    memberRepository.save(m);

    // 로그인 — 200
    login(email, password);

    Member reloaded = memberRepository.findByEmail(email).orElseThrow();
    assertThat(reloaded.getPassword()).startsWith("{argon2id}");
  }

  // ---- helpers ----
  private void signup(String email, String password, String nickname) throws Exception {
    mockMvc
        .perform(
            post("/api/members/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password, "nickname", nickname)))
        .andExpect(status().isCreated());
  }

  private String login(String email, String password) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("email", email, "password", password)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
    return body.get("accessToken").asText();
  }

  /** 단순 JSON 빌더 (key-value pairs). */
  private String json(String... kv) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < kv.length; i += 2) {
      if (i > 0) sb.append(',');
      sb.append('"').append(kv[i]).append("\":\"").append(kv[i + 1]).append('"');
    }
    return sb.append('}').toString();
  }
}

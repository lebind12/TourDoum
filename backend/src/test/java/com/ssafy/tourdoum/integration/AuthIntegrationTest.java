// 학습 친화 모드: 신규 테스트는 사용자가 작성. 본 파일은 패턴 참고용.
package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 인증 통합 테스트 — Testcontainers(MySQL 8.4 + Redis 7.4-alpine).
 *
 * <p>실행: {@code ./mvnw verify -DskipITs=false -Dtourdoum.it=true} 또는 {@code ./mvnw verify -Pit
 * -Dtourdoum.it=true}
 *
 * <p>Docker가 실행 중이어야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfSystemProperty(named = "tourdoum.it", matches = "true")
class AuthIntegrationTest {

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
    // test application.yml의 H2 설정을 MySQL Testcontainer 설정으로 명시 오버라이드.
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("회원가입 → 로그인(JWT) → /api/me(200, Bearer) → 로그아웃(204 stub) — ADR-0011 BE-1")
  void auth_full_flow() throws Exception {
    // 1. 회원가입
    mockMvc
        .perform(
            post("/api/members/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"it@example.com","password":"password123","nickname":"ituser"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("it@example.com"))
        .andExpect(jsonPath("$.nickname").value("ituser"));

    // 2. 로그인 — JWT 응답 (ADR-0011 BE-1)
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"it@example.com","password":"password123"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresInSeconds").value(900))
            .andExpect(jsonPath("$.user.email").value("it@example.com"))
            .andExpect(jsonPath("$.user.nickname").value("ituser"))
            .andExpect(jsonPath("$.user.role").value("ROLE_USER"))
            .andReturn();

    JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
    String token = body.get("accessToken").asText();
    assertThat(token).as("JWT accessToken 비어있으면 안 됨").isNotBlank();

    // 3. GET /api/me — Authorization: Bearer 헤더, 200 + 본인 정보
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("it@example.com"))
        .andExpect(jsonPath("$.nickname").value("ituser"))
        .andExpect(jsonPath("$.role").value("ROLE_USER"));

    // 4. 로그아웃 → 204 stub. BE-1는 서버 상태 없음 — denylist는 BE-2.
    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());

    // 5. logout 후에도 access token은 만료(15분)까지 여전히 유효 — BE-1 의도된 한계.
    //    BE-2에서 denylist 도입 후 401 검증으로 전환 예정.
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // 6. Authorization 헤더 없으면 401
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }
}

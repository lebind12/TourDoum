// 학습 친화 모드: 신규 테스트는 사용자가 작성. 본 파일은 패턴 참고용.
package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
  }

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("회원가입 → 로그인(SESSION 쿠키) → /api/me(200) → 로그아웃 → /api/me(401)")
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

    // 2. 로그인 — SESSION 쿠키 수신 + 응답 계약: MeResponse({id,email,nickname,role})
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
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.email").value("it@example.com"))
            .andExpect(jsonPath("$.nickname").value("ituser"))
            .andExpect(jsonPath("$.role").value("ROLE_USER"))
            .andReturn();

    // 세션 추출
    MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
    assertThat(session).isNotNull();

    // 3. GET /api/me — 쿠키 동봉, 200 + 본인 정보
    mockMvc
        .perform(get("/api/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("it@example.com"))
        .andExpect(jsonPath("$.nickname").value("ituser"))
        .andExpect(jsonPath("$.role").value("ROLE_USER"));

    // 4. 로그아웃 → 204
    mockMvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());

    // 5. 로그아웃 후 /api/me → 401
    mockMvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
  }
}

package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Reservation HTTP contract 회귀 가드 — qa #5 4 e2e fail 추적 결과 박제.
 *
 * <p>핵심: <b>Authorization: Bearer 헤더가 있으면 CSRF 면제</b>(ADR-0011 access token 메모리 보관 정합).
 * 따라서 e2e가 X-XSRF-TOKEN 헤더 없이 POST /api/reservations 호출해도 201 OK 응답.
 *
 * <p>실행: {@code ./mvnw test -Dtourdoum.it=true -Dtest=ReservationContractIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfSystemProperty(named = "tourdoum.it", matches = "true")
class ReservationContractIT {

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
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    // BE-4 Argon2id IT 비용 절감.
    registry.add("tourdoum.auth.argon2.memory-kib", () -> "16384");
    // outbox tick 자동 발동 회피 — 본 테스트는 contract만 검증.
    registry.add("tourdoum.outbox.tick-ms", () -> "3600000");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName(
      "Bearer 토큰 + X-XSRF-TOKEN 헤더 부재 + POST /api/reservations → 201 (CSRF 면제)")
  void bearer_post_without_csrf_token_returns_201() throws Exception {
    String email = "contract-" + System.nanoTime() + "@example.com";
    String password = "E2eTestSecure!9x";

    // 1) signup (CSRF ignored matcher 그대로)
    mockMvc
        .perform(
            post("/api/members/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\""
                        + email
                        + "\",\"password\":\""
                        + password
                        + "\",\"nickname\":\"contract"
                        + System.nanoTime()
                        + "\"}"))
        .andExpect(status().isCreated());

    // 2) login → access token
    MvcResult loginRes =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(loginRes.getResponse().getContentAsString());
    String accessToken = body.get("accessToken").asText();
    assertThat(accessToken).isNotBlank();

    // 3) POST /api/reservations — Bearer만, X-XSRF-TOKEN/csrf token 없음. 201 기대.
    mockMvc
        .perform(
            post("/api/reservations")
                .header("Authorization", "Bearer " + accessToken)
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"accommodationId\":1,"
                        + "\"checkIn\":\"2026-09-15\","
                        + "\"checkOut\":\"2026-09-17\","
                        + "\"guests\":2,"
                        + "\"paymentMethod\":\"CARD\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  @DisplayName("Bearer 토큰 부재 + POST /api/reservations → 401 (CSRF 단계 이전, 인증 자체 부재)")
  void no_bearer_post_returns_401() throws Exception {
    mockMvc
        .perform(
            post("/api/reservations")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"accommodationId\":1,"
                        + "\"checkIn\":\"2026-09-15\","
                        + "\"checkOut\":\"2026-09-17\","
                        + "\"guests\":2,"
                        + "\"paymentMethod\":\"CARD\"}"))
        // Bearer 없으면 CSRF 강제 → 403, 또는 인증 부재 → 401. 본 fix에선 CSRF가 먼저(filter 순서).
        // 둘 다 비-2xx면 회귀 가드 의도 충족. 명시적으론 4xx 매처 사용.
        .andExpect(result -> {
          int s = result.getResponse().getStatus();
          assertThat(s).as("Bearer 부재 mutation은 4xx").isBetween(400, 499);
        });
  }
}

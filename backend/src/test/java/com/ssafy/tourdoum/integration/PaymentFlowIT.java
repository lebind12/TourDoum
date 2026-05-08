package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.tourdoum.accommodation.Accommodation;
import com.ssafy.tourdoum.accommodation.AccommodationRepository;
import com.ssafy.tourdoum.accommodation.AccommodationType;
import com.ssafy.tourdoum.outbox.OutboxClaimState;
import com.ssafy.tourdoum.outbox.OutboxEvent;
import com.ssafy.tourdoum.outbox.OutboxEventDispatcher;
import com.ssafy.tourdoum.outbox.OutboxPublisher;
import com.ssafy.tourdoum.outbox.OutboxRepository;
import com.ssafy.tourdoum.reservation.Reservation;
import com.ssafy.tourdoum.reservation.ReservationRepository;
import com.ssafy.tourdoum.reservation.ReservationService;
import com.ssafy.tourdoum.reservation.ReservationState;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BE-14 결제 흐름 IT — Testcontainers MySQL + Redis.
 *
 * <p>검증:
 *
 * <ul>
 *   <li>POST /api/payments/start envelope 응답 + Toss DONE + state CONFIRMED.
 *   <li>Idempotency-Key 동일 호출 시 cached 응답 반환 (재실행 X).
 *   <li>RefundScheduledHandler — REFUND_PENDING outbox claim 후 REFUNDED 전이.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfSystemProperty(named = "tourdoum.it", matches = "true")
class PaymentFlowIT {

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
    // PG mock 결정성 — happy path 강제.
    registry.add("tourdoum.pg.reject-rate", () -> "0.0");
    registry.add("tourdoum.pg.timeout-rate", () -> "0.0");
    // outbox 자동 tick 회피 (테스트가 publisher.drainOnce 수동 호출).
    registry.add("tourdoum.outbox.tick-ms", () -> "3600000");
    // BE-4 Argon2 비용 절감.
    registry.add("tourdoum.auth.argon2.memory-kib", () -> "16384");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ReservationService reservationService;
  @Autowired private ReservationRepository reservationRepository;
  @Autowired private OutboxRepository outboxRepository;
  @Autowired private AccommodationRepository accommodationRepository;
  @Autowired private OutboxPublisher outboxPublisher;
  @Autowired private OutboxEventDispatcher dispatcher;
  @Autowired private com.ssafy.tourdoum.outbox.OutboxDeadLetterRepository deadLetterRepository;
  @Autowired private PlatformTransactionManager txManager;

  /** signup + login → Bearer accessToken 발급. */
  private String signupAndGetBearer() throws Exception {
    String email = "pay-" + System.nanoTime() + "@example.com";
    String password = "E2eTestSecure!9x";
    String nick = "pay" + System.nanoTime();
    mockMvc
        .perform(
            post("/api/members/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\""
                        + email
                        + "\",\"password\":\""
                        + password
                        + "\",\"nickname\":\""
                        + nick
                        + "\"}"))
        .andExpect(status().isCreated());
    MvcResult lr =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(lr.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }

  private Long createAccommodation() {
    return new TransactionTemplate(txManager)
        .execute(
            s ->
                accommodationRepository
                    .save(
                        Accommodation.builder()
                            .name("Pay test " + System.nanoTime())
                            .type(AccommodationType.HOTEL)
                            .address("addr")
                            .lat(new BigDecimal("37.5"))
                            .lng(new BigDecimal("127.0"))
                            .priceFrom(60_000)
                            .build())
                    .getId());
  }

  private Long reserve() {
    Long aid = createAccommodation();
    return new TransactionTemplate(txManager)
        .execute(
            s ->
                reservationService
                    .reserve(
                        1L,
                        aid,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 3),
                        2,
                        "k-pay-" + System.nanoTime())
                    .getId());
  }

  @Test
  @DisplayName("POST /api/payments/start → envelope OK + state CONFIRMED + tossStatus DONE")
  void payment_start_happy() throws Exception {
    String bearer = signupAndGetBearer();
    Long rid = reserve();

    MvcResult res =
        mockMvc
            .perform(
                post("/api/payments/start")
                    .header("Authorization", "Bearer " + bearer)
                    .header("Idempotency-Key", "key-happy-" + System.nanoTime())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reservationId\":" + rid + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.serverTime").exists())
            .andExpect(jsonPath("$.data.reservationId").value(String.valueOf(rid)))
            .andExpect(jsonPath("$.data.state").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.tossStatus").value("DONE"))
            .andReturn();
    assertThat(res.getResponse().getContentAsString()).contains("redirectTo");

    Reservation reloaded = reservationRepository.findById(rid).orElseThrow();
    assertThat(reloaded.getState()).isEqualTo(ReservationState.CONFIRMED);
  }

  @Test
  @DisplayName("Idempotency-Key 동일 호출 → cached envelope 반환 (state 한 번만 transition)")
  void payment_start_idempotent_cache() throws Exception {
    String bearer = signupAndGetBearer();
    Long rid = reserve();
    String key = "key-idem-" + System.nanoTime();

    MvcResult r1 =
        mockMvc
            .perform(
                post("/api/payments/start")
                    .header("Authorization", "Bearer " + bearer)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reservationId\":" + rid + "}"))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode b1 = objectMapper.readTree(r1.getResponse().getContentAsString());

    MvcResult r2 =
        mockMvc
            .perform(
                post("/api/payments/start")
                    .header("Authorization", "Bearer " + bearer)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reservationId\":" + rid + "}"))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode b2 = objectMapper.readTree(r2.getResponse().getContentAsString());

    // serverTime은 캐시된 값이라 동일 (cache hit 증명).
    assertThat(b2.get("serverTime").asText()).isEqualTo(b1.get("serverTime").asText());
    assertThat(b2.get("idempotencyKey").asText()).isEqualTo(key);
  }

  @Test
  @DisplayName(
      "RefundScheduledHandler — outbox 발행 후 publisher tick → REFUNDED 전이 + DONE outbox")
  void refund_handler_completes_transition() {
    Long rid = reserve();
    // PAYMENT_PENDING → AUTHORIZED → CAPTURED → CONFIRMED → REFUND_PENDING (RefundScheduled outbox 발행됨)
    new TransactionTemplate(txManager)
        .execute(
            s -> {
              reservationService.transitionTo(rid, ReservationState.PAYMENT_PENDING);
              reservationService.transitionTo(rid, ReservationState.AUTHORIZED);
              reservationService.transitionTo(rid, ReservationState.CAPTURED);
              reservationService.transitionTo(rid, ReservationState.CONFIRMED);
              reservationService.transitionTo(rid, ReservationState.REFUND_PENDING);
              return null;
            });

    // RefundScheduled outbox 1건 PENDING — but available_at은 +3min. 즉시 처리하기 위해 available_at을 과거로 강제.
    List<OutboxEvent> refundEvs =
        outboxRepository.findByAggregateIdAndEventType(rid, "RefundScheduled");
    assertThat(refundEvs).hasSize(1);
    OutboxEvent ev = refundEvs.get(0);
    new TransactionTemplate(txManager)
        .execute(
            s ->
                outboxRepository.markBackoff(ev.getId(), LocalDateTime.now().minusMinutes(1)));

    // publisher tick — Spring-managed publisher (실 dispatcher 사용 — RefundScheduledHandler 자동 wire).
    int processed = outboxPublisher.drainOnce();
    assertThat(processed).isGreaterThanOrEqualTo(1);

    Reservation reloaded = reservationRepository.findById(rid).orElseThrow();
    assertThat(reloaded.getState()).isEqualTo(ReservationState.REFUNDED);
    OutboxEvent doneEv = outboxRepository.findById(ev.getId()).orElseThrow();
    assertThat(doneEv.getClaimState()).isEqualTo(OutboxClaimState.DONE);
  }
}

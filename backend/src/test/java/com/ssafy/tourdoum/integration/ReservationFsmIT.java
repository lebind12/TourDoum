package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.tourdoum.accommodation.Accommodation;
import com.ssafy.tourdoum.accommodation.AccommodationRepository;
import com.ssafy.tourdoum.accommodation.AccommodationType;
import com.ssafy.tourdoum.outbox.OutboxClaimState;
import com.ssafy.tourdoum.outbox.OutboxEvent;
import com.ssafy.tourdoum.outbox.OutboxRepository;
import com.ssafy.tourdoum.reservation.Reservation;
import com.ssafy.tourdoum.reservation.ReservationFsm;
import com.ssafy.tourdoum.reservation.ReservationRepository;
import com.ssafy.tourdoum.reservation.ReservationService;
import com.ssafy.tourdoum.reservation.ReservationState;
import com.ssafy.tourdoum.reservation.ReservationTransitionLog;
import com.ssafy.tourdoum.reservation.ReservationTransitionLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-0013 BE-13 IT — V18 마이그레이션 + Reservation FSM end-to-end 도메인 흐름.
 *
 * <p>검증:
 *
 * <ul>
 *   <li>reserve → INVENTORY_RESERVED + transition_log + outbox PaymentRequested
 *   <li>transitionTo CAPTURED → CONFIRMED → outbox Notify
 *   <li>conditional UPDATE 멱등 (잘못된 prev에서 호출) → 0 row, log/outbox 미발행
 *   <li>FAILED → InventoryRelease outbox / REFUND_PENDING → RefundScheduled (지연 +3분)
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIfSystemProperty(named = "tourdoum.it", matches = "true")
class ReservationFsmIT {

  @SuppressWarnings("resource")
  @Container
  static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("tourdoum")
          .withUsername("tourdoum")
          .withPassword("tourdoum");

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
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
  }

  @Autowired private ReservationService reservationService;
  @Autowired private ReservationRepository reservationRepository;
  @Autowired private ReservationTransitionLogRepository logRepository;
  @Autowired private OutboxRepository outboxRepository;
  @Autowired private AccommodationRepository accommodationRepository;

  private Long createAccommodation() {
    Accommodation a =
        accommodationRepository.save(
            Accommodation.builder()
                .name("FSM hotel " + System.nanoTime())
                .type(AccommodationType.HOTEL)
                .address("addr")
                .lat(new BigDecimal("37.5"))
                .lng(new BigDecimal("127.0"))
                .priceFrom(60_000)
                .build());
    return a.getId();
  }

  @Test
  @DisplayName("reserve → INVENTORY_RESERVED + transition_log + outbox PaymentRequested")
  void reserve_emits_payment_requested() {
    Long aid = createAccommodation();
    String key = "k-" + System.nanoTime();
    Reservation r =
        reservationService.reserve(
            1L, aid, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), 2, key);

    Reservation reloaded = reservationRepository.findById(r.getId()).orElseThrow();
    assertThat(reloaded.getState()).isEqualTo(ReservationState.INVENTORY_RESERVED);

    List<ReservationTransitionLog> logs =
        logRepository.findByReservationIdOrderByCreatedAtAsc(r.getId());
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getFromState()).isNull();
    assertThat(logs.get(0).getToState()).isEqualTo(ReservationState.INVENTORY_RESERVED);

    List<OutboxEvent> events =
        outboxRepository.findByAggregateIdAndEventType(r.getId(), "PaymentRequested");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getClaimState()).isEqualTo(OutboxClaimState.PENDING);
  }

  @Test
  @DisplayName("happy path FSM 전이 + 멱등 no-op + CONFIRMED → Notify outbox")
  void full_chain_with_idempotency() {
    Long aid = createAccommodation();
    String key = "k-" + System.nanoTime();
    Reservation r =
        reservationService.reserve(
            2L, aid, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), 1, key);

    assertThat(reservationService.transitionTo(r.getId(), ReservationState.PAYMENT_PENDING)).isTrue();
    assertThat(reservationService.transitionTo(r.getId(), ReservationState.AUTHORIZED)).isTrue();
    assertThat(reservationService.transitionTo(r.getId(), ReservationState.CAPTURED)).isTrue();
    assertThat(reservationService.transitionTo(r.getId(), ReservationState.CONFIRMED)).isTrue();

    // 멱등 — CONFIRMED 진입 후 다시 CAPTURED 호출은 0 row.
    assertThat(reservationService.transitionTo(r.getId(), ReservationState.CAPTURED)).isFalse();

    // CONFIRMED 진입 시 Notify outbox 발행.
    List<OutboxEvent> notifies =
        outboxRepository.findByAggregateIdAndEventType(r.getId(), "Notify");
    assertThat(notifies).hasSize(1);

    // log 5건 (INVENTORY_RESERVED 시작 + 4 transition).
    List<ReservationTransitionLog> logs =
        logRepository.findByReservationIdOrderByCreatedAtAsc(r.getId());
    assertThat(logs).hasSize(5);
  }

  @Test
  @DisplayName("FAILED 진입 → InventoryRelease outbox / REFUND_PENDING → RefundScheduled +3min")
  void failed_and_refund_scheduling() {
    Long aid = createAccommodation();
    Reservation r1 =
        reservationService.reserve(
            3L, aid, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 1, "k-fail-" + System.nanoTime());
    reservationService.transitionTo(r1.getId(), ReservationState.PAYMENT_PENDING);
    assertThat(reservationService.transitionTo(r1.getId(), ReservationState.FAILED)).isTrue();

    List<OutboxEvent> releases =
        outboxRepository.findByAggregateIdAndEventType(r1.getId(), "InventoryRelease");
    assertThat(releases).hasSize(1);

    // refund — 별 reservation
    Reservation r2 =
        reservationService.reserve(
            4L, aid, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 1, "k-rfd-" + System.nanoTime());
    reservationService.transitionTo(r2.getId(), ReservationState.PAYMENT_PENDING);
    reservationService.transitionTo(r2.getId(), ReservationState.AUTHORIZED);
    reservationService.transitionTo(r2.getId(), ReservationState.CAPTURED);
    reservationService.transitionTo(r2.getId(), ReservationState.CONFIRMED);
    assertThat(reservationService.transitionTo(r2.getId(), ReservationState.REFUND_PENDING)).isTrue();

    List<OutboxEvent> refunds =
        outboxRepository.findByAggregateIdAndEventType(r2.getId(), "RefundScheduled");
    assertThat(refunds).hasSize(1);
    LocalDateTime availableAt = refunds.get(0).getAvailableAt();
    assertThat(availableAt).isAfter(LocalDateTime.now().plusMinutes(2));
  }

  @Test
  @DisplayName("FSM emit 매핑은 ADR-0013 §결정 (3) switch와 일치한다 (sanity)")
  void fsm_emit_matches_adr() {
    assertThat(ReservationFsm.emittedEventType(ReservationState.INVENTORY_RESERVED))
        .contains("PaymentRequested");
    assertThat(ReservationFsm.emittedEventType(ReservationState.CONFIRMED)).contains("Notify");
    assertThat(ReservationFsm.emittedEventType(ReservationState.FAILED))
        .contains("InventoryRelease");
    assertThat(ReservationFsm.emittedEventType(ReservationState.REFUND_PENDING))
        .contains("RefundScheduled");
  }
}

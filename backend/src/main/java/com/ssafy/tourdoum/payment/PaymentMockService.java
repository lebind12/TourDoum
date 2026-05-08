package com.ssafy.tourdoum.payment;

import com.ssafy.tourdoum.reservation.Reservation;
import com.ssafy.tourdoum.reservation.ReservationNotFoundException;
import com.ssafy.tourdoum.reservation.ReservationRepository;
import com.ssafy.tourdoum.reservation.ReservationService;
import com.ssafy.tourdoum.reservation.ReservationState;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * PG mock 도메인 서비스 — ADR-0013 Phase 1 BE-14.
 *
 * <p>실 PG 호출 없이 ReservationService.transitionTo로 단계별 전이를 시뮬레이션. happy path는 동기 체인
 * (PAYMENT_PENDING → AUTHORIZED → CAPTURED → CONFIRMED). 학습 단계 default reject/timeout 비율은
 * 0%(테스트 결정성). 테스트가 비율 + Random seed 주입.
 *
 * <p>{@link #start(Long)} 는 PG redirect URL을 반환하는 척만 한다 — Toss 공식 hosted page를 실제로
 * 띄우지 않으며, redirectTo는 mock 경로(`/payments/mock/done?...`)다.
 */
@Service
public class PaymentMockService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentMockService.class);

  private final ReservationRepository reservationRepository;
  private final ReservationService reservationService;
  private final double rejectRate;
  private final double timeoutRate;
  private final Random random;

  @org.springframework.beans.factory.annotation.Autowired
  public PaymentMockService(
      ReservationRepository reservationRepository,
      ReservationService reservationService,
      @Value("${tourdoum.pg.reject-rate:0.0}") double rejectRate,
      @Value("${tourdoum.pg.timeout-rate:0.0}") double timeoutRate) {
    this(reservationRepository, reservationService, rejectRate, timeoutRate, new Random());
  }

  /** 테스트 친화 — Random 직접 주입. */
  public PaymentMockService(
      ReservationRepository reservationRepository,
      ReservationService reservationService,
      double rejectRate,
      double timeoutRate,
      Random random) {
    this.reservationRepository = reservationRepository;
    this.reservationService = reservationService;
    this.rejectRate = rejectRate;
    this.timeoutRate = timeoutRate;
    this.random = random != null ? random : new Random();
  }

  /**
   * 결제 시작 + 동기 체인 시뮬.
   *
   * <ol>
   *   <li>예약 상태가 INVENTORY_RESERVED 이어야 함 (그 외는 IllegalStateException).
   *   <li>INVENTORY_RESERVED → PAYMENT_PENDING (idempotent transition).
   *   <li>rejectRate 시뮬: PAYMENT_PENDING → REJECTED (Toss ABORTED).
   *   <li>timeoutRate 시뮬: PAYMENT_PENDING → FAILED (Toss EXPIRED).
   *   <li>그 외: PAYMENT_PENDING → AUTHORIZED → CAPTURED → CONFIRMED (Toss DONE).
   * </ol>
   *
   * @param reservationId 예약 PK
   * @return 종료 시점 PaymentStartData (final state + Toss status + redirect)
   */
  public PaymentStartData start(Long reservationId) {
    Reservation reservation =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

    if (reservation.getState() != ReservationState.INVENTORY_RESERVED
        && reservation.getState() != ReservationState.PAYMENT_PENDING) {
      throw new IllegalStateException(
          "결제는 INVENTORY_RESERVED/PAYMENT_PENDING 상태에서만 시작 가능 — actual=" + reservation.getState());
    }

    // INVENTORY_RESERVED → PAYMENT_PENDING (멱등 — 이미 PAYMENT_PENDING이면 no-op).
    reservationService.transitionTo(reservationId, ReservationState.PAYMENT_PENDING);

    double roll = random.nextDouble();
    if (roll < rejectRate) {
      reservationService.transitionTo(reservationId, ReservationState.REJECTED);
      LOG.info("[pg-mock] reservationId={} REJECTED (mock)", reservationId);
      return new PaymentStartData(
          String.valueOf(reservationId),
          ReservationState.REJECTED,
          TossStatus.ABORTED,
          "/payments/mock/aborted?id=" + reservationId);
    }
    if (roll < rejectRate + timeoutRate) {
      reservationService.transitionTo(reservationId, ReservationState.FAILED);
      LOG.info("[pg-mock] reservationId={} FAILED/EXPIRED (mock)", reservationId);
      return new PaymentStartData(
          String.valueOf(reservationId),
          ReservationState.FAILED,
          TossStatus.EXPIRED,
          "/payments/mock/expired?id=" + reservationId);
    }

    // happy path
    reservationService.transitionTo(reservationId, ReservationState.AUTHORIZED);
    reservationService.transitionTo(reservationId, ReservationState.CAPTURED);
    reservationService.transitionTo(reservationId, ReservationState.CONFIRMED);
    LOG.info("[pg-mock] reservationId={} CONFIRMED (mock)", reservationId);
    return new PaymentStartData(
        String.valueOf(reservationId),
        ReservationState.CONFIRMED,
        TossStatus.DONE,
        "/payments/mock/done?id=" + reservationId);
  }
}

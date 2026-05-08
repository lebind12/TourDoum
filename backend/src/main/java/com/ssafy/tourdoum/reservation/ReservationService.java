package com.ssafy.tourdoum.reservation;

import com.ssafy.tourdoum.accommodation.Accommodation;
import com.ssafy.tourdoum.accommodation.AccommodationNotFoundException;
import com.ssafy.tourdoum.accommodation.AccommodationRepository;
import com.ssafy.tourdoum.notification.NotificationService;
import com.ssafy.tourdoum.notification.NotificationType;
import com.ssafy.tourdoum.outbox.OutboxEvent;
import com.ssafy.tourdoum.outbox.OutboxRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 예약 서비스. */
@Service
@Transactional(readOnly = true)
public class ReservationService {

  /** 청소비 (원 단위, 고정값). FE store의 CLEANING_FEE = 20_000 동일. */
  static final int CLEANING_FEE = 20_000;

  /** ADR-0013 §결정 (4) — RefundScheduled 지연 발행 (학습 단계 default). */
  static final java.time.Duration REFUND_DELAY = java.time.Duration.ofMinutes(3);

  private final ReservationRepository reservationRepository;
  private final AccommodationRepository accommodationRepository;
  private final NotificationService notificationService;
  private final ReservationTransitionLogRepository transitionLogRepository;
  private final OutboxRepository outboxRepository;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public ReservationService(
      ReservationRepository reservationRepository,
      AccommodationRepository accommodationRepository,
      NotificationService notificationService,
      ReservationTransitionLogRepository transitionLogRepository,
      OutboxRepository outboxRepository) {
    this(
        reservationRepository,
        accommodationRepository,
        notificationService,
        transitionLogRepository,
        outboxRepository,
        Clock.systemDefaultZone());
  }

  /** 테스트 친화 — Clock 주입. */
  public ReservationService(
      ReservationRepository reservationRepository,
      AccommodationRepository accommodationRepository,
      NotificationService notificationService,
      ReservationTransitionLogRepository transitionLogRepository,
      OutboxRepository outboxRepository,
      Clock clock) {
    this.reservationRepository = reservationRepository;
    this.accommodationRepository = accommodationRepository;
    this.notificationService = notificationService;
    this.transitionLogRepository = transitionLogRepository;
    this.outboxRepository = outboxRepository;
    this.clock = clock != null ? clock : Clock.systemDefaultZone();
  }

  /**
   * 예약 견적 계산 (step 1-2, 트랜잭션 없음).
   *
   * <p>숙박의 priceFrom 기준으로 박수 * 단가 + 청소비를 계산한다.
   *
   * @throws AccommodationNotFoundException 숙박 미존재
   * @throws IllegalArgumentException 체크아웃이 체크인보다 이전/동일한 경우
   */
  public ReservationQuoteResponse quote(ReservationQuoteRequest request) {
    Accommodation accommodation =
        accommodationRepository
            .findById(request.accommodationId())
            .orElseThrow(() -> new AccommodationNotFoundException(request.accommodationId()));

    int pricePerNight = accommodation.getPriceFrom() != null ? accommodation.getPriceFrom() : 0;

    int nights = nights(request.checkIn(), request.checkOut());
    int totalPrice = pricePerNight * nights + CLEANING_FEE;

    return new ReservationQuoteResponse(
        request.accommodationId(),
        request.checkIn(),
        request.checkOut(),
        nights,
        request.guests(),
        pricePerNight,
        CLEANING_FEE,
        totalPrice);
  }

  /**
   * 예약 확정 (step 3, 트랜잭션).
   *
   * <p>Idempotency-Key가 이미 DB에 있으면 기존 예약을 그대로 반환 (멱등성 보장). 없으면 신규 INSERT.
   *
   * @param memberId 로그인 회원 PK
   * @param request 예약 확정 요청
   * @param idempotencyKey Idempotency-Key 헤더 값
   */
  @Transactional
  public ReservationResponse confirm(
      Long memberId, ReservationConfirmRequest request, String idempotencyKey) {
    // 멱등성: 동일 키가 존재하면 기존 예약 반환
    Optional<Reservation> existing = reservationRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return ReservationResponse.from(existing.get());
    }

    Accommodation accommodation =
        accommodationRepository
            .findById(request.accommodationId())
            .orElseThrow(() -> new AccommodationNotFoundException(request.accommodationId()));

    int pricePerNight = accommodation.getPriceFrom() != null ? accommodation.getPriceFrom() : 0;
    int nights = nights(request.checkIn(), request.checkOut());
    int totalPrice = pricePerNight * nights + CLEANING_FEE;

    Reservation reservation =
        Reservation.builder()
            .memberId(memberId)
            .accommodationId(request.accommodationId())
            .checkIn(request.checkIn())
            .checkOut(request.checkOut())
            .guests(request.guests())
            .totalPrice(totalPrice)
            .paymentMethod(request.paymentMethod())
            .idempotencyKey(idempotencyKey)
            .initialState(ReservationState.CONFIRMED)
            .build();

    Reservation saved = reservationRepository.save(reservation);

    // BE-13.1 wiring (qa #6 baseline 발견 — 누락) — legacy confirm() 경로도 transition_log + outbox
    // SOT artifact를 INSERT해야 ADR-0013 §결정 (15) Phase 1 outbox drain rate / audit trail 측정 가능.
    // 본 메서드는 PG/FSM stage를 우회하고 곧장 CONFIRMED를 박는 단순 흐름이므로 from_state=null + to=CONFIRMED.
    transitionLogRepository.save(
        ReservationTransitionLog.builder()
            .reservationId(saved.getId())
            .fromState(null)
            .toState(ReservationState.CONFIRMED)
            .metadata("{\"via\":\"confirm\",\"idempotencyKey\":\"" + idempotencyKey + "\"}")
            .build());
    emitOutbox(saved.getId(), ReservationState.CONFIRMED);

    ReservationResponse response = ReservationResponse.from(saved);
    notificationService.publish(
        memberId,
        NotificationType.RESERVATION_CONFIRMED,
        "예약이 확정되었습니다",
        accommodation.getName() + " 예약이 성공적으로 확정되었습니다.",
        "/reservations/me");
    return response;
  }

  /**
   * 내 예약 목록 조회.
   *
   * @param memberId 로그인 회원 PK
   */
  public List<ReservationResponse> myList(Long memberId) {
    return reservationRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
        .map(ReservationResponse::from)
        .toList();
  }

  /**
   * 예약 취소.
   *
   * @param reservationId 예약 PK
   * @param memberId 로그인 회원 PK
   * @throws ReservationNotFoundException 예약 미존재 (→ 404)
   * @throws ReservationForbiddenException 취소 권한 없음 (→ 403)
   */
  @Transactional
  public ReservationResponse cancel(Long reservationId, Long memberId) {
    Reservation reservation =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

    if (!reservation.getMemberId().equals(memberId)) {
      throw new ReservationForbiddenException(reservationId);
    }

    reservation.cancel();
    return ReservationResponse.from(reservation);
  }

  // ===========================================================================
  // ADR-0013 BE-13 — FSM 골격 (reserve / transitionTo)
  // ===========================================================================
  // 본 영역은 13-state Reservation FSM의 진입점. 기존 quote/confirm/myList/cancel는 BE-14에서
  // FSM으로 통합 예정 — 본 task는 신규 메서드만 추가하고 legacy 흐름은 그대로 둔다.

  /**
   * INVENTORY_RESERVED 상태로 신규 예약 박제 — ADR-0013 §결정 (3).
   *
   * <p>기존 confirm 경로와는 별도. 본 메서드는 PG webhook 이전 단계까지(재고 확보 + outbox PaymentRequested 발행)
   * 책임지며, AUTHORIZED/CAPTURED/CONFIRMED는 후속 transitionTo 호출로 이어진다.
   *
   * <p>idempotencyKey 동일 호출은 멱등 — 기존 row 반환.
   */
  @Transactional
  public Reservation reserve(
      Long memberId,
      Long accommodationId,
      LocalDate checkIn,
      LocalDate checkOut,
      int guests,
      String idempotencyKey) {
    Optional<Reservation> existing = reservationRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return existing.get();
    }

    Accommodation accommodation =
        accommodationRepository
            .findById(accommodationId)
            .orElseThrow(() -> new AccommodationNotFoundException(accommodationId));

    int pricePerNight = accommodation.getPriceFrom() != null ? accommodation.getPriceFrom() : 0;
    int nightCount = nights(checkIn, checkOut);
    int totalPrice = pricePerNight * nightCount + CLEANING_FEE;

    Reservation reservation =
        Reservation.builder()
            .memberId(memberId)
            .accommodationId(accommodationId)
            .checkIn(checkIn)
            .checkOut(checkOut)
            .guests(guests)
            .totalPrice(totalPrice)
            .paymentMethod(PaymentMethod.CARD) // BE-14 PG mock에서 실제 method 채움
            .idempotencyKey(idempotencyKey)
            .initialState(ReservationState.INVENTORY_RESERVED)
            .build();
    Reservation saved = reservationRepository.save(reservation);

    transitionLogRepository.save(
        ReservationTransitionLog.builder()
            .reservationId(saved.getId())
            .fromState(null)
            .toState(ReservationState.INVENTORY_RESERVED)
            .metadata("{\"idempotencyKey\":\"" + idempotencyKey + "\"}")
            .build());

    // 외부 경계 분리 — PG mock 호출은 outbox publisher가 처리 (BE-15).
    emitOutbox(saved.getId(), ReservationState.INVENTORY_RESERVED);
    return saved;
  }

  /**
   * FSM 전이 — conditional UPDATE (`WHERE state IN expectedPrev`). rowsUpdated == 0이면 멱등 no-op.
   *
   * <p>정상 전이 시 transition_log INSERT + 보상/알림 outbox 발행. 잘못된 newState
   * (allowedPrev 매핑 부재)는 {@link IllegalArgumentException}.
   *
   * @return 전이 성공 여부 (true = 1 row 업데이트, false = 멱등 no-op)
   */
  @Transactional
  public boolean transitionTo(Long reservationId, ReservationState newState) {
    Set<ReservationState> allowedPrev = ReservationFsm.allowedPrev(newState);
    if (allowedPrev.isEmpty()) {
      throw new IllegalArgumentException(
          "전이 대상 state가 FSM에 정의되지 않았습니다 (혹은 시작 상태): " + newState);
    }

    LocalDateTime now = LocalDateTime.now(clock);
    int rows = reservationRepository.transitionState(reservationId, newState, allowedPrev, now);
    if (rows == 0) {
      // 다른 replica가 이미 처리 / 잘못된 prev — 멱등 no-op.
      return false;
    }

    Reservation r =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

    // from_state 추정: allowedPrev 단일이면 그것, 다중이면 null로 박제 (관측만 — 정확한 from은 별도 SELECT로
    // 미리 박았어야 함). 본 task는 단순화.
    ReservationState fromState = allowedPrev.size() == 1 ? allowedPrev.iterator().next() : null;
    transitionLogRepository.save(
        ReservationTransitionLog.builder()
            .reservationId(reservationId)
            .fromState(fromState)
            .toState(newState)
            .metadata(null)
            .build());

    emitOutbox(r.getId(), newState);
    return true;
  }

  /** 보상/알림 outbox 발행 — {@link ReservationFsm#emittedEventType} 매핑 적용. 없으면 no-op. */
  private void emitOutbox(Long reservationId, ReservationState newState) {
    ReservationFsm.emittedEventType(newState)
        .ifPresent(
            type -> {
              LocalDateTime availableAt = LocalDateTime.now(clock);
              if ("RefundScheduled".equals(type)) {
                availableAt = availableAt.plus(REFUND_DELAY);
              }
              outboxRepository.save(
                  OutboxEvent.builder()
                      .aggregateId(reservationId)
                      .eventType(type)
                      .payload(
                          "{\"reservationId\":" + reservationId + ",\"state\":\"" + newState + "\"}")
                      .availableAt(availableAt)
                      .build());
            });
  }

  private int nights(LocalDate checkIn, LocalDate checkOut) {
    int nights = (int) checkIn.until(checkOut).getDays();
    if (nights <= 0) {
      throw new IllegalArgumentException(
          "체크아웃은 체크인보다 늦어야 합니다. checkIn=" + checkIn + ", checkOut=" + checkOut);
    }
    return nights;
  }
}

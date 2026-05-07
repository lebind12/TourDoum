package com.ssafy.tourdoum.reservation;

import com.ssafy.tourdoum.accommodation.Accommodation;
import com.ssafy.tourdoum.accommodation.AccommodationNotFoundException;
import com.ssafy.tourdoum.accommodation.AccommodationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 예약 서비스. */
@Service
@Transactional(readOnly = true)
public class ReservationService {

  /** 청소비 (원 단위, 고정값). FE store의 CLEANING_FEE = 20_000 동일. */
  static final int CLEANING_FEE = 20_000;

  private final ReservationRepository reservationRepository;
  private final AccommodationRepository accommodationRepository;

  public ReservationService(
      ReservationRepository reservationRepository,
      AccommodationRepository accommodationRepository) {
    this.reservationRepository = reservationRepository;
    this.accommodationRepository = accommodationRepository;
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
            .build();

    return ReservationResponse.from(reservationRepository.save(reservation));
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

  private int nights(LocalDate checkIn, LocalDate checkOut) {
    int nights = (int) checkIn.until(checkOut).getDays();
    if (nights <= 0) {
      throw new IllegalArgumentException(
          "체크아웃은 체크인보다 늦어야 합니다. checkIn=" + checkIn + ", checkOut=" + checkOut);
    }
    return nights;
  }
}

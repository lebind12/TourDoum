package com.ssafy.tourdoum.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.ssafy.tourdoum.accommodation.Accommodation;
import com.ssafy.tourdoum.accommodation.AccommodationRepository;
import com.ssafy.tourdoum.accommodation.AccommodationType;
import com.ssafy.tourdoum.notification.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ReservationService 단위 테스트 (Mockito). 학습 친화 모드 — 양산 금지. */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

  @Mock private ReservationRepository reservationRepository;
  @Mock private AccommodationRepository accommodationRepository;
  @Mock private NotificationService notificationService;

  @InjectMocks private ReservationService reservationService;

  private Accommodation buildAccommodation(int priceFrom) {
    return Accommodation.builder()
        .name("테스트 호텔")
        .type(AccommodationType.HOTEL)
        .address("서울시 중구")
        .lat(new BigDecimal("37.5636"))
        .lng(new BigDecimal("126.9826"))
        .priceFrom(priceFrom)
        .build();
  }

  @Test
  @DisplayName("quote — pricePerNight × nights + CLEANING_FEE 계산이 올바름")
  void quote_calculatesCorrectTotal() {
    // given
    Long accommodationId = 1L;
    LocalDate checkIn = LocalDate.of(2026, 6, 1);
    LocalDate checkOut = LocalDate.of(2026, 6, 4); // 3박
    int pricePerNight = 100_000;

    given(accommodationRepository.findById(accommodationId))
        .willReturn(Optional.of(buildAccommodation(pricePerNight)));

    ReservationQuoteRequest request =
        new ReservationQuoteRequest(accommodationId, checkIn, checkOut, 2);

    // when
    ReservationQuoteResponse response = reservationService.quote(request);

    // then
    assertThat(response.nights()).isEqualTo(3);
    assertThat(response.pricePerNight()).isEqualTo(pricePerNight);
    assertThat(response.cleaningFee()).isEqualTo(ReservationService.CLEANING_FEE);
    assertThat(response.totalPrice())
        .isEqualTo(pricePerNight * 3 + ReservationService.CLEANING_FEE);
  }

  @Test
  @DisplayName("confirm — 동일 idempotencyKey 재요청 시 save 호출 없이 기존 예약 반환 (멱등성)")
  void confirm_idempotent_returnExistingReservation() {
    // given
    String iKey = "test-uuid-1234";
    Reservation existing =
        Reservation.builder()
            .memberId(1L)
            .accommodationId(1L)
            .checkIn(LocalDate.of(2026, 6, 1))
            .checkOut(LocalDate.of(2026, 6, 3))
            .guests(2)
            .totalPrice(220_000)
            .paymentMethod(PaymentMethod.CARD)
            .idempotencyKey(iKey)
            .build();

    given(reservationRepository.findByIdempotencyKey(iKey)).willReturn(Optional.of(existing));

    ReservationConfirmRequest request =
        new ReservationConfirmRequest(
            1L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), 2, PaymentMethod.CARD);

    // when
    ReservationResponse response = reservationService.confirm(1L, request, iKey);

    // then
    assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CARD);
    assertThat(response.totalPrice()).isEqualTo(220_000);
    // save를 호출하지 않아야 함
    then(reservationRepository).should().findByIdempotencyKey(iKey);
    then(reservationRepository).should(org.mockito.Mockito.never()).save(any());
  }
}

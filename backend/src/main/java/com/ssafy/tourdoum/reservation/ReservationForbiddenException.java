package com.ssafy.tourdoum.reservation;

/** 예약 취소 권한 없음 예외 → GlobalExceptionHandler → 403. */
public class ReservationForbiddenException extends RuntimeException {

  public ReservationForbiddenException(Long reservationId) {
    super("예약을 취소할 권한이 없습니다. id=" + reservationId);
  }
}

package com.ssafy.tourdoum.reservation;

/** 예약 미존재 예외 → GlobalExceptionHandler → 404. */
public class ReservationNotFoundException extends RuntimeException {

  public ReservationNotFoundException(Long id) {
    super("예약을 찾을 수 없습니다. id=" + id);
  }
}

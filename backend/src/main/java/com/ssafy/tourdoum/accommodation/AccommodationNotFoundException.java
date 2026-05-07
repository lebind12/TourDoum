package com.ssafy.tourdoum.accommodation;

/** 숙박을 찾을 수 없을 때 발생하는 예외. GlobalExceptionHandler에서 404로 매핑된다. */
public class AccommodationNotFoundException extends RuntimeException {

  public AccommodationNotFoundException(Long id) {
    super("숙박을 찾을 수 없습니다. id=" + id);
  }
}

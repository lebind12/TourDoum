package com.ssafy.tourdoum.attraction;

/** 여행지를 찾을 수 없을 때 발생하는 예외. GlobalExceptionHandler에서 404로 매핑된다. */
public class AttractionNotFoundException extends RuntimeException {

  public AttractionNotFoundException(Long id) {
    super("여행지를 찾을 수 없습니다. id=" + id);
  }
}

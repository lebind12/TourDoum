package com.ssafy.tourdoum.review;

/** 후기 미존재 예외 → GlobalExceptionHandler → 404. */
public class ReviewNotFoundException extends RuntimeException {

  public ReviewNotFoundException(Long id) {
    super("후기를 찾을 수 없습니다. id=" + id);
  }
}

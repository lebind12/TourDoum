package com.ssafy.tourdoum.review;

/** 후기 삭제 권한 없음 예외 → GlobalExceptionHandler → 403. */
public class ReviewForbiddenException extends RuntimeException {

  public ReviewForbiddenException(Long reviewId) {
    super("후기를 삭제할 권한이 없습니다. id=" + reviewId);
  }
}

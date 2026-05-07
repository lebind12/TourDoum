package com.ssafy.tourdoum.plan;

/** 여행 계획 접근 권한 없음 예외 (→ 403). */
public class PlanForbiddenException extends RuntimeException {

  public PlanForbiddenException(Long id) {
    super("여행 계획에 대한 권한이 없습니다. id=" + id);
  }
}

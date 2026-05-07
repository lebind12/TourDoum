package com.ssafy.tourdoum.plan;

/** 여행 계획 미존재 예외 (→ 404). */
public class PlanNotFoundException extends RuntimeException {

  public PlanNotFoundException(Long id) {
    super("여행 계획을 찾을 수 없습니다. id=" + id);
  }
}

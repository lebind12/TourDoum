package com.ssafy.tourdoum.plan;

/** 여행 계획 아이템 응답 DTO. */
public record PlanItemResponse(
    Long id,
    Long planId,
    int dayIndex,
    int orderIndex,
    PlanItemTargetType targetType,
    Long targetId,
    String memo) {

  /** PlanItem 엔티티 → 응답 변환. */
  public static PlanItemResponse from(PlanItem item) {
    return new PlanItemResponse(
        item.getId(),
        item.getPlan().getId(),
        item.getDayIndex(),
        item.getOrderIndex(),
        item.getTargetType(),
        item.getTargetId(),
        item.getMemo());
  }
}

package com.ssafy.tourdoum.plan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 여행 계획 응답 DTO (아이템 포함). */
public record PlanResponse(
    Long id,
    Long memberId,
    String title,
    LocalDate startDate,
    LocalDate endDate,
    LocalDateTime createdAt,
    List<PlanItemResponse> items) {

  /** Plan 엔티티 → 응답 변환 (아이템 포함). */
  public static PlanResponse from(Plan plan) {
    List<PlanItemResponse> itemResponses =
        plan.getItems().stream().map(PlanItemResponse::from).toList();
    return new PlanResponse(
        plan.getId(),
        plan.getMemberId(),
        plan.getTitle(),
        plan.getStartDate(),
        plan.getEndDate(),
        plan.getCreatedAt(),
        itemResponses);
  }

  /** Plan 엔티티 → 응답 변환 (아이템 없음, 목록 조회용). */
  public static PlanResponse fromSummary(Plan plan) {
    return new PlanResponse(
        plan.getId(),
        plan.getMemberId(),
        plan.getTitle(),
        plan.getStartDate(),
        plan.getEndDate(),
        plan.getCreatedAt(),
        List.of());
  }
}

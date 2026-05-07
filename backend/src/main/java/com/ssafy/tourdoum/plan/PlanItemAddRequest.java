package com.ssafy.tourdoum.plan;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 여행 계획 아이템 추가 요청. */
public record PlanItemAddRequest(
    @Min(0) int dayIndex,
    @Min(0) int orderIndex,
    @NotNull PlanItemTargetType targetType,
    @NotNull Long targetId,
    String memo) {}

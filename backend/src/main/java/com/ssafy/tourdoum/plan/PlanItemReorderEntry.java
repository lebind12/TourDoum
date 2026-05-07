package com.ssafy.tourdoum.plan;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** reorder 요청 배열의 단일 항목. */
public record PlanItemReorderEntry(
    @NotNull Long id, @Min(0) int dayIndex, @Min(0) int orderIndex) {}

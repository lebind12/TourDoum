package com.ssafy.tourdoum.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** reorder 요청 — 아이템 목록 전체를 갱신. */
public record PlanItemReorderRequest(@NotNull @Valid List<PlanItemReorderEntry> items) {}

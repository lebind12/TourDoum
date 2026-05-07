package com.ssafy.tourdoum.plan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** 여행 계획 생성 요청. */
public record PlanCreateRequest(
    @NotBlank String title, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}

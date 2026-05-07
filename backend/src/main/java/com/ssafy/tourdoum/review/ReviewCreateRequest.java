package com.ssafy.tourdoum.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 후기 작성 요청 DTO. */
public record ReviewCreateRequest(
    @NotNull(message = "targetType은 필수입니다.") ReviewTargetType targetType,
    @NotNull(message = "targetId는 필수입니다.") Long targetId,
    @Min(value = 1, message = "별점은 1 이상이어야 합니다.") @Max(value = 5, message = "별점은 5 이하이어야 합니다.")
        int rating,
    @Size(max = 200, message = "제목은 200자 이하이어야 합니다.") String title,
    @NotNull(message = "내용은 필수입니다.") @Size(min = 1, message = "내용을 입력해 주세요.") String content) {}

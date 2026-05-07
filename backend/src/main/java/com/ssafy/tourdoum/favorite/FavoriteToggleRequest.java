package com.ssafy.tourdoum.favorite;

import jakarta.validation.constraints.NotNull;

/** 즐겨찾기 토글 요청 DTO. */
public record FavoriteToggleRequest(
    @NotNull(message = "targetType은 필수입니다.") FavoriteTargetType targetType,
    @NotNull(message = "targetId는 필수입니다.") Long targetId) {}

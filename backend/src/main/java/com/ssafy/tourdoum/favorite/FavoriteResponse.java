package com.ssafy.tourdoum.favorite;

import java.time.LocalDateTime;

/** 즐겨찾기 항목 응답 DTO. */
public record FavoriteResponse(
    Long id, FavoriteTargetType targetType, Long targetId, LocalDateTime createdAt) {

  public static FavoriteResponse from(Favorite favorite) {
    return new FavoriteResponse(
        favorite.getId(),
        favorite.getTargetType(),
        favorite.getTargetId(),
        favorite.getCreatedAt());
  }
}

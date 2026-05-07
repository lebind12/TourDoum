package com.ssafy.tourdoum.favorite;

/**
 * 즐겨찾기 토글 결과 DTO.
 *
 * @param added true = 추가됨, false = 제거됨
 */
public record FavoriteToggleResponse(boolean added) {}

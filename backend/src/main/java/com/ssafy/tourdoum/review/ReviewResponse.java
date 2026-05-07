package com.ssafy.tourdoum.review;

import java.time.LocalDateTime;

/** 후기 응답 DTO. */
public record ReviewResponse(
    Long id,
    Long memberId,
    String authorNickname,
    ReviewTargetType targetType,
    Long targetId,
    int rating,
    String title,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  /** 닉네임 포함 변환. */
  public static ReviewResponse from(Review review, String authorNickname) {
    return new ReviewResponse(
        review.getId(),
        review.getMemberId(),
        authorNickname,
        review.getTargetType(),
        review.getTargetId(),
        review.getRating(),
        review.getTitle(),
        review.getContent(),
        review.getCreatedAt(),
        review.getUpdatedAt());
  }

  /** 닉네임 미제공 시 fallback (레거시/테스트 용). */
  public static ReviewResponse from(Review review) {
    return from(review, null);
  }
}

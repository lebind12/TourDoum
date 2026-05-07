package com.ssafy.tourdoum.review;

import java.time.LocalDateTime;

/** 후기 응답 DTO. */
public record ReviewResponse(
    Long id,
    Long memberId,
    ReviewTargetType targetType,
    Long targetId,
    int rating,
    String title,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static ReviewResponse from(Review review) {
    return new ReviewResponse(
        review.getId(),
        review.getMemberId(),
        review.getTargetType(),
        review.getTargetId(),
        review.getRating(),
        review.getTitle(),
        review.getContent(),
        review.getCreatedAt(),
        review.getUpdatedAt());
  }
}

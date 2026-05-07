package com.ssafy.tourdoum.review;

import com.ssafy.tourdoum.attraction.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 후기 서비스. */
@Service
@Transactional(readOnly = true)
public class ReviewService {

  private final ReviewRepository reviewRepository;

  public ReviewService(ReviewRepository reviewRepository) {
    this.reviewRepository = reviewRepository;
  }

  /**
   * 후기 작성.
   *
   * @param memberId 로그인 회원 PK
   * @param request 작성 요청
   */
  @Transactional
  public ReviewResponse create(Long memberId, ReviewCreateRequest request) {
    Review review =
        Review.builder()
            .memberId(memberId)
            .targetType(request.targetType())
            .targetId(request.targetId())
            .rating(request.rating())
            .title(request.title())
            .content(request.content())
            .build();
    return ReviewResponse.from(reviewRepository.save(review));
  }

  /**
   * 특정 대상의 후기 목록 조회.
   *
   * @param targetType ATTRACTION 또는 ACCOMMODATION
   * @param targetId 대상 PK
   * @param page 페이지 번호 (0-based)
   * @param size 페이지 크기
   */
  public PageResponse<ReviewResponse> list(
      ReviewTargetType targetType, Long targetId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<ReviewResponse> result =
        reviewRepository
            .findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId, pageable)
            .map(ReviewResponse::from);
    return PageResponse.from(result);
  }

  /**
   * 특정 대상의 평균 별점 + 후기 건수 집계.
   *
   * @param targetType ATTRACTION 또는 ACCOMMODATION
   * @param targetId 대상 PK
   */
  public ReviewSummaryResponse summary(ReviewTargetType targetType, Long targetId) {
    Object[] row = reviewRepository.aggregateByTarget(targetType, targetId);
    double avg = row[0] != null ? ((Number) row[0]).doubleValue() : 0.0;
    long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
    return new ReviewSummaryResponse(avg, count);
  }

  /**
   * 후기 삭제 (작성자만 가능).
   *
   * @param reviewId 후기 PK
   * @param memberId 로그인 회원 PK
   * @throws ReviewNotFoundException 해당 후기가 없을 때 (→ 404)
   * @throws ReviewForbiddenException 작성자가 아닐 때 (→ 403)
   */
  @Transactional
  public void delete(Long reviewId, Long memberId) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new ReviewNotFoundException(reviewId));
    if (!review.getMemberId().equals(memberId)) {
      throw new ReviewForbiddenException(reviewId);
    }
    reviewRepository.delete(review);
  }
}

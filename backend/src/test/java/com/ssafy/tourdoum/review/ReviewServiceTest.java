package com.ssafy.tourdoum.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.ssafy.tourdoum.notification.NotificationService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ReviewService 단위 테스트 (Mockito). 학습 친화 모드 — 양산 금지. */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  @Mock private ReviewRepository reviewRepository;
  @Mock private NotificationService notificationService;

  @InjectMocks private ReviewService reviewService;

  @Test
  @DisplayName("create — 요청 내용이 저장된 후 ReviewResponse로 반환됨")
  void create_savesAndReturnsResponse() {
    // given
    Long memberId = 1L;
    ReviewCreateRequest request =
        new ReviewCreateRequest(ReviewTargetType.ATTRACTION, 42L, 5, "멋진 곳", "정말 좋았어요!");

    Review saved =
        Review.builder()
            .memberId(memberId)
            .targetType(ReviewTargetType.ATTRACTION)
            .targetId(42L)
            .rating(5)
            .title("멋진 곳")
            .content("정말 좋았어요!")
            .build();
    given(reviewRepository.save(any(Review.class))).willReturn(saved);

    // when
    ReviewResponse response = reviewService.create(memberId, request);

    // then
    assertThat(response.memberId()).isEqualTo(memberId);
    assertThat(response.targetType()).isEqualTo(ReviewTargetType.ATTRACTION);
    assertThat(response.targetId()).isEqualTo(42L);
    assertThat(response.rating()).isEqualTo(5);
    assertThat(response.content()).isEqualTo("정말 좋았어요!");
  }

  @Test
  @DisplayName("summary — 집계 결과가 ReviewSummaryResponse로 매핑됨")
  void summary_mapAggregateResult() {
    // given
    ReviewTargetType type = ReviewTargetType.ACCOMMODATION;
    Long targetId = 3L;
    given(reviewRepository.aggregateByTarget(type, targetId)).willReturn(new Object[] {4.2, 10L});

    // when
    ReviewSummaryResponse result = reviewService.summary(type, targetId);

    // then
    assertThat(result.avgRating()).isEqualTo(4.2);
    assertThat(result.count()).isEqualTo(10L);
  }

  @Test
  @DisplayName("delete — 작성자가 아닌 회원이 삭제 시 ReviewForbiddenException 발생")
  void delete_forbiddenForNonAuthor() {
    // given
    Long reviewId = 99L;
    Long authorId = 1L;
    Long attackerId = 2L;
    Review review =
        Review.builder()
            .memberId(authorId)
            .targetType(ReviewTargetType.ATTRACTION)
            .targetId(1L)
            .rating(4)
            .title(null)
            .content("좋아요")
            .build();
    given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));

    // when / then
    assertThatThrownBy(() -> reviewService.delete(reviewId, attackerId))
        .isInstanceOf(ReviewForbiddenException.class)
        .hasMessageContaining(String.valueOf(reviewId));
  }
}

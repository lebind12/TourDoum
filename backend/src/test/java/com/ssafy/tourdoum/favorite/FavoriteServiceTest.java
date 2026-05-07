package com.ssafy.tourdoum.favorite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** FavoriteService 단위 테스트 (Mockito). 학습 친화 모드 — 양산 금지. */
@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

  @Mock private FavoriteRepository favoriteRepository;

  @InjectMocks private FavoriteService favoriteService;

  @Test
  @DisplayName("toggle — 즐겨찾기 미존재 시 저장 후 true(추가됨) 반환")
  void toggle_add() {
    // given
    Long memberId = 1L;
    FavoriteTargetType type = FavoriteTargetType.ATTRACTION;
    Long targetId = 42L;

    given(favoriteRepository.existsByMemberIdAndTargetTypeAndTargetId(memberId, type, targetId))
        .willReturn(false);

    // when
    boolean added = favoriteService.toggle(memberId, type, targetId);

    // then
    assertThat(added).isTrue();
    then(favoriteRepository).should().save(any(Favorite.class));
  }

  @Test
  @DisplayName("toggle — 이미 존재 시 삭제 후 false(제거됨) 반환")
  void toggle_remove() {
    // given
    Long memberId = 1L;
    FavoriteTargetType type = FavoriteTargetType.ACCOMMODATION;
    Long targetId = 7L;

    given(favoriteRepository.existsByMemberIdAndTargetTypeAndTargetId(memberId, type, targetId))
        .willReturn(true);

    // when
    boolean added = favoriteService.toggle(memberId, type, targetId);

    // then
    assertThat(added).isFalse();
    then(favoriteRepository)
        .should()
        .deleteByMemberIdAndTargetTypeAndTargetId(memberId, type, targetId);
  }

  @Test
  @DisplayName("list — 회원의 즐겨찾기 목록 반환")
  void list_returnsMemberFavorites() {
    // given
    Long memberId = 2L;
    Favorite f1 =
        Favorite.builder()
            .memberId(memberId)
            .targetType(FavoriteTargetType.ATTRACTION)
            .targetId(10L)
            .build();
    Favorite f2 =
        Favorite.builder()
            .memberId(memberId)
            .targetType(FavoriteTargetType.ACCOMMODATION)
            .targetId(3L)
            .build();
    given(favoriteRepository.findByMemberId(memberId)).willReturn(List.of(f1, f2));

    // when
    List<FavoriteResponse> result = favoriteService.list(memberId);

    // then
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(FavoriteResponse::targetType)
        .containsExactly(FavoriteTargetType.ATTRACTION, FavoriteTargetType.ACCOMMODATION);
  }
}

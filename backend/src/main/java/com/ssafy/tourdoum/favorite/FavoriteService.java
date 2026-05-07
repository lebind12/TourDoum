package com.ssafy.tourdoum.favorite;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 즐겨찾기 서비스. */
@Service
@Transactional(readOnly = true)
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;

  public FavoriteService(FavoriteRepository favoriteRepository) {
    this.favoriteRepository = favoriteRepository;
  }

  /**
   * 즐겨찾기 목록 조회.
   *
   * @param memberId 로그인 회원 PK
   */
  public List<FavoriteResponse> list(Long memberId) {
    return favoriteRepository.findByMemberId(memberId).stream()
        .map(FavoriteResponse::from)
        .toList();
  }

  /**
   * 즐겨찾기 토글.
   *
   * <p>이미 존재하면 삭제(removed), 없으면 추가(added).
   *
   * @param memberId 로그인 회원 PK
   * @param targetType ATTRACTION 또는 ACCOMMODATION
   * @param targetId 대상 PK
   * @return true = 추가됨, false = 제거됨
   */
  @Transactional
  public boolean toggle(Long memberId, FavoriteTargetType targetType, Long targetId) {
    if (favoriteRepository.existsByMemberIdAndTargetTypeAndTargetId(
        memberId, targetType, targetId)) {
      favoriteRepository.deleteByMemberIdAndTargetTypeAndTargetId(memberId, targetType, targetId);
      return false;
    }

    favoriteRepository.save(
        Favorite.builder().memberId(memberId).targetType(targetType).targetId(targetId).build());
    return true;
  }
}

package com.ssafy.tourdoum.favorite;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 즐겨찾기 레포지토리. */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

  List<Favorite> findByMemberId(Long memberId);

  boolean existsByMemberIdAndTargetTypeAndTargetId(
      Long memberId, FavoriteTargetType targetType, Long targetId);

  void deleteByMemberIdAndTargetTypeAndTargetId(
      Long memberId, FavoriteTargetType targetType, Long targetId);
}

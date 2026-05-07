package com.ssafy.tourdoum.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 후기 레포지토리. */
public interface ReviewRepository extends JpaRepository<Review, Long> {

  /** 특정 대상의 후기 목록 (페이지네이션, 최신순). */
  Page<Review> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
      ReviewTargetType targetType, Long targetId, Pageable pageable);

  /** 특정 대상의 평균 별점과 후기 건수 집계. */
  @Query(
      """
      SELECT AVG(r.rating), COUNT(r)
      FROM Review r
      WHERE r.targetType = :targetType AND r.targetId = :targetId
      """)
  Object[] aggregateByTarget(
      @Param("targetType") ReviewTargetType targetType, @Param("targetId") Long targetId);
}

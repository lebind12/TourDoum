package com.ssafy.tourdoum.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 후기 레포지토리. */
public interface ReviewRepository extends JpaRepository<Review, Long> {

  /**
   * 특정 대상의 후기 목록 — Member JOIN으로 닉네임 함께 조회 (N+1 방지).
   *
   * <p>Object[0] = Review, Object[1] = Member
   */
  @Query(
      value =
          """
          SELECT r, m
          FROM Review r
          JOIN Member m ON m.id = r.memberId
          WHERE r.targetType = :targetType AND r.targetId = :targetId
          ORDER BY r.createdAt DESC
          """,
      countQuery =
          """
          SELECT COUNT(r)
          FROM Review r
          WHERE r.targetType = :targetType AND r.targetId = :targetId
          """)
  Page<Object[]> findByTargetWithAuthor(
      @Param("targetType") ReviewTargetType targetType,
      @Param("targetId") Long targetId,
      Pageable pageable);

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

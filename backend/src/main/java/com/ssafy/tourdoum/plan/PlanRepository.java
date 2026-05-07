package com.ssafy.tourdoum.plan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 여행 계획 JPA 레포지토리. */
public interface PlanRepository extends JpaRepository<Plan, Long> {

  /** 회원의 계획 목록 (최신순). */
  List<Plan> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}

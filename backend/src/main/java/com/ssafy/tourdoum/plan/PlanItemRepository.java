package com.ssafy.tourdoum.plan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 여행 계획 아이템 JPA 레포지토리. */
public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {

  /** 계획의 아이템 목록 (dayIndex, orderIndex 순). */
  List<PlanItem> findByPlanIdOrderByDayIndexAscOrderIndexAsc(Long planId);

  /** 특정 계획에 속하는 아이템 단건 조회 (계획 + 아이템 소속 동시 검증). */
  java.util.Optional<PlanItem> findByIdAndPlanId(Long id, Long planId);
}

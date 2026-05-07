package com.ssafy.tourdoum.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 여행 계획 일정 아이템 엔티티. dayIndex + orderIndex 로 drag reorder 지원. */
@Entity
@Table(name = "plan_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @Column(name = "day_index", nullable = false)
  private int dayIndex;

  @Column(name = "order_index", nullable = false)
  private int orderIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 20)
  private PlanItemTargetType targetType;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @Column(length = 500)
  private String memo;

  @Builder
  public PlanItem(
      Plan plan,
      int dayIndex,
      int orderIndex,
      PlanItemTargetType targetType,
      Long targetId,
      String memo) {
    this.plan = plan;
    this.dayIndex = dayIndex;
    this.orderIndex = orderIndex;
    this.targetType = targetType;
    this.targetId = targetId;
    this.memo = memo;
  }

  /** reorder 시 dayIndex / orderIndex 갱신. */
  public void updateOrder(int dayIndex, int orderIndex) {
    this.dayIndex = dayIndex;
    this.orderIndex = orderIndex;
  }
}

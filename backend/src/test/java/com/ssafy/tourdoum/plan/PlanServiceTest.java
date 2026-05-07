package com.ssafy.tourdoum.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PlanService 단위 테스트 (Mockito). 학습 친화 모드 — 양산 금지. */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

  @Mock private PlanRepository planRepository;
  @Mock private PlanItemRepository planItemRepository;

  @InjectMocks private PlanService planService;

  private Plan buildPlan(Long memberId) {
    return Plan.builder()
        .memberId(memberId)
        .title("제주도 여행")
        .startDate(LocalDate.of(2026, 7, 1))
        .endDate(LocalDate.of(2026, 7, 5))
        .build();
  }

  @Test
  @DisplayName("reorderItems — 반환 목록이 dayIndex + orderIndex 오름차순으로 정렬됨")
  void reorderItems_returnsSortedByDayAndOrder() {
    // given
    Long planId = 1L;
    Long memberId = 10L;
    Plan plan = buildPlan(memberId);

    // 아이템 2개: day=0 order=1 이 먼저, day=0 order=0 이 나중에 저장된 경우
    PlanItem item1 =
        PlanItem.builder()
            .plan(plan)
            .dayIndex(0)
            .orderIndex(1)
            .targetType(PlanItemTargetType.ATTRACTION)
            .targetId(100L)
            .memo(null)
            .build();
    PlanItem item2 =
        PlanItem.builder()
            .plan(plan)
            .dayIndex(0)
            .orderIndex(0)
            .targetType(PlanItemTargetType.ACCOMMODATION)
            .targetId(200L)
            .memo(null)
            .build();

    given(planRepository.findById(planId)).willReturn(Optional.of(plan));
    // findByPlanId → 역순으로 반환 (DB 정렬 미반영 시뮬레이션)
    given(planItemRepository.findByPlanIdOrderByDayIndexAscOrderIndexAsc(planId))
        .willReturn(List.of(item1, item2));

    PlanItemReorderRequest request =
        new PlanItemReorderRequest(List.of()); // id 없음 → updateOrder 호출 없음

    // when
    List<PlanItemResponse> result = planService.reorderItems(planId, memberId, request);

    // then — 서비스 내부 sort: dayIndex ASC, orderIndex ASC → item2(0,0) 먼저
    assertThat(result).hasSize(2);
    assertThat(result.get(0).orderIndex()).isEqualTo(0); // item2
    assertThat(result.get(1).orderIndex()).isEqualTo(1); // item1
  }

  @Test
  @DisplayName("create — 시작일 >= 종료일이면 IllegalArgumentException 발생")
  void create_invalidDateRange_throwsException() {
    // given
    Long memberId = 1L;
    PlanCreateRequest request =
        new PlanCreateRequest(
            "제주도", LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 1)); // 종료일 < 시작일

    // when / then
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> planService.create(memberId, request));
  }

  @Test
  @DisplayName("deleteItem — 본인 계획의 아이템이면 삭제됨")
  void deleteItem_ownerCanDelete() {
    // given
    Long planId = 1L;
    Long itemId = 10L;
    Long memberId = 5L;
    Plan plan = buildPlan(memberId);
    PlanItem item =
        PlanItem.builder()
            .plan(plan)
            .dayIndex(0)
            .orderIndex(0)
            .targetType(PlanItemTargetType.ATTRACTION)
            .targetId(42L)
            .memo(null)
            .build();

    given(planRepository.findById(planId)).willReturn(Optional.of(plan));
    given(planItemRepository.findByIdAndPlanId(itemId, planId)).willReturn(Optional.of(item));

    // when
    planService.deleteItem(planId, itemId, memberId);

    // then
    verify(planItemRepository).delete(item);
  }

  @Test
  @DisplayName("deleteItem — 존재하지 않는 아이템이면 PlanNotFoundException 발생")
  void deleteItem_itemNotFound_throwsException() {
    // given
    Long planId = 1L;
    Long itemId = 99L;
    Long memberId = 5L;
    Plan plan = buildPlan(memberId);

    given(planRepository.findById(planId)).willReturn(Optional.of(plan));
    given(planItemRepository.findByIdAndPlanId(itemId, planId)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> planService.deleteItem(planId, itemId, memberId))
        .isInstanceOf(PlanNotFoundException.class)
        .hasMessageContaining(String.valueOf(itemId));
  }
}

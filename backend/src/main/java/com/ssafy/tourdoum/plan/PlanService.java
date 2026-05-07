package com.ssafy.tourdoum.plan;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 여행 계획 서비스. */
@Service
@Transactional(readOnly = true)
public class PlanService {

  private final PlanRepository planRepository;
  private final PlanItemRepository planItemRepository;

  public PlanService(PlanRepository planRepository, PlanItemRepository planItemRepository) {
    this.planRepository = planRepository;
    this.planItemRepository = planItemRepository;
  }

  /**
   * 여행 계획 생성.
   *
   * @param memberId 로그인 회원 PK
   * @param request 제목 + 날짜 범위
   */
  @Transactional
  public PlanResponse create(Long memberId, PlanCreateRequest request) {
    if (!request.startDate().isBefore(request.endDate())) {
      throw new IllegalArgumentException(
          "종료일은 시작일보다 늦어야 합니다. startDate="
              + request.startDate()
              + ", endDate="
              + request.endDate());
    }
    Plan plan =
        planRepository.save(
            Plan.builder()
                .memberId(memberId)
                .title(request.title())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build());
    return PlanResponse.from(plan);
  }

  /**
   * 내 여행 계획 목록 (아이템 없는 요약).
   *
   * @param memberId 로그인 회원 PK
   */
  public List<PlanResponse> myList(Long memberId) {
    return planRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
        .map(PlanResponse::fromSummary)
        .toList();
  }

  /**
   * 여행 계획 상세 조회 (아이템 포함).
   *
   * @param planId 계획 PK
   * @param memberId 로그인 회원 PK (권한 체크)
   * @throws PlanNotFoundException 계획 미존재 (→ 404)
   * @throws PlanForbiddenException 본인 계획 아님 (→ 403)
   */
  public PlanResponse detail(Long planId, Long memberId) {
    Plan plan = findAndVerify(planId, memberId);
    return PlanResponse.from(plan);
  }

  /**
   * 아이템 추가.
   *
   * @param planId 계획 PK
   * @param memberId 로그인 회원 PK (권한 체크)
   * @param request 아이템 정보
   */
  @Transactional
  public PlanItemResponse addItem(Long planId, Long memberId, PlanItemAddRequest request) {
    Plan plan = findAndVerify(planId, memberId);
    PlanItem item =
        PlanItem.builder()
            .plan(plan)
            .dayIndex(request.dayIndex())
            .orderIndex(request.orderIndex())
            .targetType(request.targetType())
            .targetId(request.targetId())
            .memo(request.memo())
            .build();
    plan.addItem(item);
    planItemRepository.save(item);
    return PlanItemResponse.from(item);
  }

  /**
   * 아이템 reorder (dayIndex + orderIndex 일괄 갱신).
   *
   * <p>FE drag & drop 완료 후 전체 아이템 순서를 서버에 반영한다.
   *
   * @param planId 계획 PK
   * @param memberId 로그인 회원 PK (권한 체크)
   * @param request {id, dayIndex, orderIndex} 목록
   */
  @Transactional
  public List<PlanItemResponse> reorderItems(
      Long planId, Long memberId, PlanItemReorderRequest request) {
    findAndVerify(planId, memberId);

    // id → PlanItem map (id가 null인 항목은 skip — 단위 테스트 안전)
    List<PlanItem> items = planItemRepository.findByPlanIdOrderByDayIndexAscOrderIndexAsc(planId);
    Map<Long, PlanItem> itemMap =
        items.stream()
            .filter(i -> i.getId() != null)
            .collect(Collectors.toMap(PlanItem::getId, i -> i, (a, b) -> b));

    for (PlanItemReorderEntry entry : request.items()) {
      PlanItem item = itemMap.get(entry.id());
      if (item != null) {
        item.updateOrder(entry.dayIndex(), entry.orderIndex());
      }
    }
    // dirty checking → flush on commit
    return items.stream()
        .sorted(
            java.util.Comparator.comparingInt(PlanItem::getDayIndex)
                .thenComparingInt(PlanItem::getOrderIndex))
        .map(PlanItemResponse::from)
        .toList();
  }

  /**
   * 아이템 단건 삭제.
   *
   * <p>계획 소유권 체크 후 아이템이 해당 계획에 속하는지 검증한다.
   *
   * @param planId 계획 PK
   * @param itemId 아이템 PK
   * @param memberId 로그인 회원 PK (권한 체크)
   * @throws PlanNotFoundException 계획 또는 아이템 미존재 (→ 404)
   * @throws PlanForbiddenException 본인 계획 아님 (→ 403)
   */
  @Transactional
  public void deleteItem(Long planId, Long itemId, Long memberId) {
    findAndVerify(planId, memberId);
    PlanItem item =
        planItemRepository
            .findByIdAndPlanId(itemId, planId)
            .orElseThrow(() -> new PlanNotFoundException(itemId));
    planItemRepository.delete(item);
  }

  /**
   * 여행 계획 삭제 (cascade → 아이템도 삭제).
   *
   * @param planId 계획 PK
   * @param memberId 로그인 회원 PK (권한 체크)
   */
  @Transactional
  public void delete(Long planId, Long memberId) {
    Plan plan = findAndVerify(planId, memberId);
    planRepository.delete(plan);
  }

  private Plan findAndVerify(Long planId, Long memberId) {
    Plan plan =
        planRepository.findById(planId).orElseThrow(() -> new PlanNotFoundException(planId));
    if (!plan.getMemberId().equals(memberId)) {
      throw new PlanForbiddenException(planId);
    }
    return plan;
  }
}

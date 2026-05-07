package com.ssafy.tourdoum.plan;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 계획 REST 컨트롤러.
 *
 * <p>전 엔드포인트 SESSION 인증 필수.
 */
@Tag(name = "Plan", description = "여행 계획 CRUD + 일정 drag-reorder API")
@RestController
@RequestMapping("/api/plans")
public class PlanController {

  private final PlanService planService;
  private final MemberRepository memberRepository;

  public PlanController(PlanService planService, MemberRepository memberRepository) {
    this.planService = planService;
    this.memberRepository = memberRepository;
  }

  /** 여행 계획 생성. */
  @Operation(summary = "여행 계획 생성", description = "제목 + 날짜 범위로 새 여행 계획을 생성한다. SESSION 쿠키 필수.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "생성된 계획"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @SecurityRequirement(name = "SESSION")
  @PostMapping
  public ResponseEntity<PlanResponse> create(
      @Valid @RequestBody PlanCreateRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.status(HttpStatus.CREATED).body(planService.create(memberId, request));
  }

  /** 내 여행 계획 목록. */
  @Operation(summary = "내 여행 계획 목록", description = "로그인 회원의 여행 계획 목록(요약)을 최신순으로 반환한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "계획 목록"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @SecurityRequirement(name = "SESSION")
  @GetMapping("/me")
  public ResponseEntity<List<PlanResponse>> myList(
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(planService.myList(memberId));
  }

  /** 여행 계획 상세 (아이템 포함). */
  @Operation(summary = "여행 계획 상세", description = "계획 ID로 상세 정보와 아이템 목록을 반환한다. 본인 계획만 조회 가능.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "계획 상세"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
    @ApiResponse(responseCode = "404", description = "계획 미존재")
  })
  @SecurityRequirement(name = "SESSION")
  @GetMapping("/{id}")
  public ResponseEntity<PlanResponse> detail(
      @Parameter(description = "계획 PK") @PathVariable Long id,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(planService.detail(id, memberId));
  }

  /** 아이템 추가. */
  @Operation(summary = "일정 아이템 추가", description = "계획에 관광지/숙박 아이템을 추가한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "추가된 아이템"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
    @ApiResponse(responseCode = "404", description = "계획 미존재")
  })
  @SecurityRequirement(name = "SESSION")
  @PostMapping("/{id}/items")
  public ResponseEntity<PlanItemResponse> addItem(
      @Parameter(description = "계획 PK") @PathVariable Long id,
      @Valid @RequestBody PlanItemAddRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(planService.addItem(id, memberId, request));
  }

  /** 아이템 reorder (drag & drop 결과 반영). */
  @Operation(
      summary = "일정 아이템 재정렬",
      description = "drag & drop 완료 후 아이템 전체의 dayIndex + orderIndex를 일괄 갱신한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "갱신된 아이템 목록"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
    @ApiResponse(responseCode = "404", description = "계획 미존재")
  })
  @SecurityRequirement(name = "SESSION")
  @PatchMapping("/{id}/items/reorder")
  public ResponseEntity<List<PlanItemResponse>> reorderItems(
      @Parameter(description = "계획 PK") @PathVariable Long id,
      @Valid @RequestBody PlanItemReorderRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(planService.reorderItems(id, memberId, request));
  }

  /** 여행 계획 삭제. */
  @Operation(summary = "여행 계획 삭제", description = "계획 ID로 계획과 전체 아이템을 삭제한다. 본인 계획만 삭제 가능.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 완료"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
    @ApiResponse(responseCode = "404", description = "계획 미존재")
  })
  @SecurityRequirement(name = "SESSION")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "계획 PK") @PathVariable Long id,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    planService.delete(id, memberId);
    return ResponseEntity.noContent().build();
  }

  private Long resolveMemberId(UserDetails userDetails) {
    Member member =
        memberRepository
            .findByEmail(userDetails.getUsername())
            .orElseThrow(
                () -> new UsernameNotFoundException("회원 없음: " + userDetails.getUsername()));
    return member.getId();
  }
}

package com.ssafy.tourdoum.review;

import com.ssafy.tourdoum.attraction.PageResponse;
import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 후기 REST 컨트롤러.
 *
 * <p>인증 정책: POST/DELETE는 SESSION 인증 필수. GET은 permitAll.
 */
@Tag(name = "Review", description = "후기 CRUD + 별점 집계 API")
@Validated
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

  private final ReviewService reviewService;
  private final MemberRepository memberRepository;

  public ReviewController(ReviewService reviewService, MemberRepository memberRepository) {
    this.reviewService = reviewService;
    this.memberRepository = memberRepository;
  }

  /**
   * 후기 목록 조회 (인증 불필요).
   *
   * @param targetType ATTRACTION 또는 ACCOMMODATION
   * @param targetId 대상 PK
   */
  @Operation(
      summary = "후기 목록 조회",
      description = "대상(targetType + targetId)의 후기 목록을 최신순으로 반환한다. 인증 불필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "후기 목록 (페이지 정보 포함)"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터")
  })
  @GetMapping
  public ResponseEntity<PageResponse<ReviewResponse>> list(
      @Parameter(description = "대상 타입 (ATTRACTION | ACCOMMODATION)") @RequestParam
          ReviewTargetType targetType,
      @Parameter(description = "대상 PK") @RequestParam Long targetId,
      @Parameter(description = "페이지 번호 (0-based)") @RequestParam(defaultValue = "0") @Min(0)
          int page,
      @Parameter(description = "페이지 크기 (기본 20, 최대 100)")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size) {
    return ResponseEntity.ok(reviewService.list(targetType, targetId, page, size));
  }

  /**
   * 후기 집계 조회 — 평균 별점 + 건수 (인증 불필요).
   *
   * @param targetType ATTRACTION 또는 ACCOMMODATION
   * @param targetId 대상 PK
   */
  @Operation(summary = "후기 집계 (avgRating + count)", description = "대상의 평균 별점과 후기 건수를 반환한다. 인증 불필요.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "평균 별점 + 건수")})
  @GetMapping("/summary")
  public ResponseEntity<ReviewSummaryResponse> summary(
      @Parameter(description = "대상 타입 (ATTRACTION | ACCOMMODATION)") @RequestParam
          ReviewTargetType targetType,
      @Parameter(description = "대상 PK") @RequestParam Long targetId) {
    return ResponseEntity.ok(reviewService.summary(targetType, targetId));
  }

  /**
   * 후기 작성 (인증 필수).
   *
   * @param request 후기 내용
   * @param userDetails 현재 로그인한 사용자
   */
  @Operation(summary = "후기 작성", description = "대상에 대한 후기를 작성한다. SESSION 쿠키 필수.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "작성된 후기"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터"),
    @ApiResponse(responseCode = "401", description = "인증되지 않은 요청")
  })
  @SecurityRequirement(name = "SESSION")
  @PostMapping
  public ResponseEntity<ReviewResponse> create(
      @Valid @RequestBody ReviewCreateRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    ReviewResponse response = reviewService.create(memberId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * 후기 삭제 (작성자 인증 필수).
   *
   * @param id 후기 PK
   * @param userDetails 현재 로그인한 사용자
   */
  @Operation(summary = "후기 삭제", description = "작성자만 삭제할 수 있다. SESSION 쿠키 필수.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(responseCode = "401", description = "인증되지 않은 요청"),
    @ApiResponse(responseCode = "403", description = "삭제 권한 없음"),
    @ApiResponse(responseCode = "404", description = "해당 후기 없음")
  })
  @SecurityRequirement(name = "SESSION")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "후기 PK") @PathVariable Long id,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    reviewService.delete(id, memberId);
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

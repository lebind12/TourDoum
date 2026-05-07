package com.ssafy.tourdoum.reservation;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예약 REST 컨트롤러.
 *
 * <p>인증 정책: 전 엔드포인트 SESSION 인증 필수.
 */
@Tag(name = "Reservation", description = "숙박 예약 3단계 API (견적·확정·목록·취소)")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

  private final ReservationService reservationService;
  private final MemberRepository memberRepository;

  public ReservationController(
      ReservationService reservationService, MemberRepository memberRepository) {
    this.reservationService = reservationService;
    this.memberRepository = memberRepository;
  }

  /**
   * step 1-2: 예약 견적 (가격 계산, 저장 없음).
   *
   * @param request 숙박 ID + 날짜 + 인원
   * @param userDetails 로그인 사용자
   */
  @Operation(
      summary = "예약 견적 계산",
      description = "숙박 ID + 날짜 + 인원으로 박수·단가·청소비·합계를 계산한다. DB 저장 없음. SESSION 쿠키 필수.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "견적 결과"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "404", description = "숙박 미존재")
  })
  @SecurityRequirement(name = "SESSION")
  @PostMapping("/quote")
  public ResponseEntity<ReservationQuoteResponse> quote(
      @Valid @RequestBody ReservationQuoteRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    resolveMemberId(userDetails); // 인증 검증 (결과값은 quote에 불필요)
    return ResponseEntity.ok(reservationService.quote(request));
  }

  /**
   * step 3: 예약 확정 (INSERT + 멱등성 보장).
   *
   * <p>Idempotency-Key 헤더 필수. 동일 키로 재요청 시 기존 예약 반환 (201이 아닌 200).
   *
   * @param request 예약 확정 정보
   * @param idempotencyKey 클라이언트 생성 UUID (중복 방지용)
   * @param userDetails 로그인 사용자
   */
  @Operation(
      summary = "예약 확정",
      description = "Idempotency-Key 헤더로 중복 방지. 동일 키 재요청 시 기존 예약 그대로 반환. SESSION 쿠키 필수.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "예약 확정 완료"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "404", description = "숙박 미존재")
  })
  @SecurityRequirement(name = "SESSION")
  @PostMapping
  public ResponseEntity<ReservationResponse> confirm(
      @Valid @RequestBody ReservationConfirmRequest request,
      @Parameter(description = "중복 방지용 클라이언트 UUID (예: crypto.randomUUID())")
          @RequestHeader("Idempotency-Key")
          String idempotencyKey,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    ReservationResponse response = reservationService.confirm(memberId, request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * 내 예약 목록 조회.
   *
   * @param userDetails 로그인 사용자
   */
  @Operation(summary = "내 예약 목록", description = "로그인 회원의 예약 목록을 최신순으로 반환한다. SESSION 쿠키 필수.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "예약 목록"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @SecurityRequirement(name = "SESSION")
  @GetMapping("/me")
  public ResponseEntity<List<ReservationResponse>> myList(
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(reservationService.myList(memberId));
  }

  /**
   * 예약 취소.
   *
   * @param id 예약 PK
   * @param userDetails 로그인 사용자
   */
  @Operation(summary = "예약 취소", description = "예약 ID로 예약을 취소한다. 본인 예약만 취소 가능. SESSION 쿠키 필수.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "취소된 예약 정보"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "403", description = "취소 권한 없음"),
    @ApiResponse(responseCode = "404", description = "예약 미존재")
  })
  @SecurityRequirement(name = "SESSION")
  @PostMapping("/{id}/cancel")
  public ResponseEntity<ReservationResponse> cancel(
      @Parameter(description = "예약 PK") @PathVariable Long id,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(reservationService.cancel(id, memberId));
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

package com.ssafy.tourdoum.favorite;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 즐겨찾기 REST 컨트롤러.
 *
 * <p>인증 정책: SecurityConfig에서 /api/favorites/** 는 authenticated.
 */
@Tag(name = "Favorite", description = "즐겨찾기 토글/조회 API (인증 필수)")
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

  private final FavoriteService favoriteService;
  private final MemberRepository memberRepository;

  public FavoriteController(FavoriteService favoriteService, MemberRepository memberRepository) {
    this.favoriteService = favoriteService;
    this.memberRepository = memberRepository;
  }

  /**
   * 즐겨찾기 목록 조회.
   *
   * @param userDetails 현재 로그인한 사용자 (세션 기반)
   */
  @Operation(summary = "즐겨찾기 목록 조회", description = "현재 로그인한 회원의 즐겨찾기 목록을 조회한다. SESSION 쿠키 필수.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "즐겨찾기 목록"),
    @ApiResponse(responseCode = "401", description = "인증되지 않은 요청")
  })
  @SecurityRequirement(name = "SESSION")
  @GetMapping
  public ResponseEntity<List<FavoriteResponse>> list(
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(favoriteService.list(memberId));
  }

  /**
   * 즐겨찾기 토글 (추가 또는 제거).
   *
   * @param request targetType + targetId
   * @param userDetails 현재 로그인한 사용자
   */
  @Operation(
      summary = "즐겨찾기 토글",
      description =
          "targetType(ATTRACTION/ACCOMMODATION) + targetId를 전달하면 즐겨찾기를 추가하거나 제거한다."
              + " 응답의 added=true면 추가, false면 제거됨.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "토글 결과 (added: true/false)"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터"),
    @ApiResponse(responseCode = "401", description = "인증되지 않은 요청")
  })
  @SecurityRequirement(name = "SESSION")
  @PostMapping("/toggle")
  public ResponseEntity<FavoriteToggleResponse> toggle(
      @Valid @RequestBody FavoriteToggleRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    boolean added = favoriteService.toggle(memberId, request.targetType(), request.targetId());
    return ResponseEntity.ok(new FavoriteToggleResponse(added));
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

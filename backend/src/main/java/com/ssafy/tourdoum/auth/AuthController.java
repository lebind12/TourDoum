package com.ssafy.tourdoum.auth;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 API. POST /api/auth/login, POST /api/auth/logout 은 SecurityConfig 필터/핸들러 처리.
 *
 * <p>로그인/로그아웃은 Spring Security 필터 체인이 처리하므로 별도 핸들러 메서드가 없다. Swagger 문서에는 수동 @Operation으로 표기.
 */
@Tag(name = "Auth", description = "인증·인가 API (로그인/로그아웃/내 정보)")
@RestController
@RequestMapping("/api")
public class AuthController {

  private final MemberRepository memberRepository;

  public AuthController(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  /**
   * GET /api/me — 현재 로그인한 회원 정보. 인증 필요.
   *
   * <p>SESSION 쿠키가 없거나 만료됐으면 401 Unauthorized.
   */
  @Operation(summary = "내 정보 조회", description = "현재 세션의 로그인 회원 정보를 반환한다. SESSION 쿠키 필수.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그인한 회원 정보"),
    @ApiResponse(responseCode = "401", description = "인증되지 않은 요청 (SESSION 쿠키 없음·만료)")
  })
  @SecurityRequirement(name = "SESSION")
  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal UserDetails userDetails) {
    Member member =
        memberRepository
            .findByEmail(userDetails.getUsername())
            .orElseThrow(
                () -> new UsernameNotFoundException("회원 없음: " + userDetails.getUsername()));
    return MeResponse.from(member);
  }
}

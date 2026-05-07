package com.ssafy.tourdoum.auth;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API — JWT 기반 (ADR-0011 BE-1, #61).
 *
 * <ul>
 *   <li>{@code POST /api/auth/login} → access token 발급
 *   <li>{@code POST /api/auth/logout} → 204 stub (BE-2 denylist 추가 예정)
 *   <li>{@code GET /api/me} → SecurityContext의 {@link UserDetails} 기반 회원 조회
 * </ul>
 *
 * <p>이전 form-login + Spring Session 흐름은 ADR-0011로 폐기. {@link JwtAuthenticationFilter}가
 * Authorization: Bearer header를 SecurityContext로 변환한다.
 */
@Tag(name = "Auth", description = "인증·인가 API (로그인/로그아웃/내 정보)")
@RestController
@RequestMapping("/api")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final JwtTokenProvider tokenProvider;
  private final MemberRepository memberRepository;

  public AuthController(
      AuthenticationManager authenticationManager,
      JwtTokenProvider tokenProvider,
      MemberRepository memberRepository) {
    this.authenticationManager = authenticationManager;
    this.tokenProvider = tokenProvider;
    this.memberRepository = memberRepository;
  }

  @Operation(
      summary = "로그인",
      description = "이메일/비밀번호로 인증 후 access token (RS256, 15분 TTL)을 Bearer 형태로 발급한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "JWT + 회원 정보"),
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
  })
  @PostMapping("/auth/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    String email = request.email() != null ? request.email().trim() : "";
    String password = request.password() != null ? request.password() : "";
    try {
      authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    } catch (AuthenticationException ex) {
      throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.", ex);
    }
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("회원 없음: " + email));
    JwtTokenProvider.IssuedToken issued = tokenProvider.issueAccessToken(member);
    return ResponseEntity.ok(LoginResponse.of(issued, MeResponse.from(member)));
  }

  @Operation(
      summary = "로그아웃 (stub)",
      description = "BE-1에서는 서버 상태 없음. BE-2에서 refresh denylist 추가 예정. 클라이언트는 메모리 토큰을 폐기.")
  @ApiResponse(responseCode = "204", description = "OK (no-op)")
  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @Operation(summary = "내 정보 조회", description = "현재 access token의 SecurityContext에서 회원 정보를 반환한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "로그인한 회원 정보"),
    @ApiResponse(responseCode = "401", description = "Bearer 토큰 없음 또는 무효")
  })
  @SecurityRequirement(name = "bearerAuth")
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

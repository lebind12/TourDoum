package com.ssafy.tourdoum.auth;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
 * 인증 API — JWT 기반.
 *
 * <ul>
 *   <li>{@code POST /api/auth/login} — JWT access + refresh 발급 (BE-1 + BE-2)
 *   <li>{@code POST /api/auth/refresh} — refresh rotation (BE-2, #63)
 *   <li>{@code POST /api/auth/logout} — family 폐기 + access denylist (BE-2, #63)
 *   <li>{@code GET /api/me} — SecurityContext 기반 회원 조회
 * </ul>
 */
@Tag(name = "Auth", description = "인증·인가 API (로그인/리프레시/로그아웃/내 정보)")
@RestController
@RequestMapping("/api")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final AuthService authService;
  private final JwtTokenProvider tokenProvider;
  private final MemberRepository memberRepository;

  public AuthController(
      AuthenticationManager authenticationManager,
      AuthService authService,
      JwtTokenProvider tokenProvider,
      MemberRepository memberRepository) {
    this.authenticationManager = authenticationManager;
    this.authService = authService;
    this.tokenProvider = tokenProvider;
    this.memberRepository = memberRepository;
  }

  @Operation(
      summary = "로그인",
      description = "이메일/비밀번호 인증 후 access(15m) + refresh(14d) 토큰 발급. family Redis 박제.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "JWT 응답"),
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
    return ResponseEntity.ok(authService.issueOnLogin(member));
  }

  @Operation(
      summary = "Refresh rotation",
      description = "refresh token 검증 → 새 access + 새 refresh 발급. replay 감지 시 family 전체 폐기.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "새 토큰 쌍"),
    @ApiResponse(responseCode = "401", description = "refresh 만료/서명 오류/replay 감지")
  })
  @PostMapping("/auth/refresh")
  public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ResponseEntity.ok(authService.rotate(request.refreshToken()));
  }

  @Operation(
      summary = "로그아웃",
      description = "refresh token이 동봉되면 family 전체 폐기. 현재 access는 denylist에 추가되어 만료까지 무효.")
  @ApiResponse(responseCode = "204", description = "OK")
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(
      @RequestBody(required = false) LogoutRequest request, HttpServletRequest httpRequest) {
    String accessJti = (String) httpRequest.getAttribute(JwtAuthenticationFilter.ATTR_ACCESS_JTI);
    Object expiresAtAttr =
        httpRequest.getAttribute(JwtAuthenticationFilter.ATTR_ACCESS_EXPIRES_AT_EPOCH_SECOND);
    long ttlSeconds = 0;
    if (expiresAtAttr instanceof Number n) {
      ttlSeconds = n.longValue() - java.time.Instant.now().getEpochSecond();
    }

    String familyId = null;
    if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
      try {
        Claims claims = tokenProvider.parseRefreshToken(request.refreshToken());
        familyId = claims.get("family_id", String.class);
      } catch (JwtException ignore) {
        // 무효 refresh는 무시 — access denylist만 처리.
      }
    }
    authService.logout(accessJti, ttlSeconds, familyId);
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

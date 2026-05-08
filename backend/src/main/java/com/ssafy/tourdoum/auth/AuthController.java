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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API — JWT 기반.
 *
 * <ul>
 *   <li>{@code POST /api/auth/login} — JWT access + refresh 발급. BE-3: refresh를 httpOnly cookie로도
 *       박는다. body의 refreshToken은 한시 호환(deprecated).
 *   <li>{@code POST /api/auth/refresh} — refresh rotation. BE-3: cookie 우선, body fallback.
 *   <li>{@code POST /api/auth/logout} — family 폐기 + access denylist. BE-3: cookie 우선, body
 *       fallback. 응답에 cookie clear 동봉.
 *   <li>{@code GET /api/me} — SecurityContext 기반 회원 조회.
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
  private final AuthCookieService authCookieService;
  private final LoginLockoutService lockoutService;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicy;
  private final PasswordResetService passwordResetService;

  public AuthController(
      AuthenticationManager authenticationManager,
      AuthService authService,
      JwtTokenProvider tokenProvider,
      MemberRepository memberRepository,
      AuthCookieService authCookieService,
      LoginLockoutService lockoutService,
      PasswordEncoder passwordEncoder,
      PasswordPolicyValidator passwordPolicy,
      PasswordResetService passwordResetService) {
    this.authenticationManager = authenticationManager;
    this.authService = authService;
    this.tokenProvider = tokenProvider;
    this.memberRepository = memberRepository;
    this.authCookieService = authCookieService;
    this.lockoutService = lockoutService;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.passwordResetService = passwordResetService;
  }

  @Operation(
      summary = "로그인",
      description =
          "이메일/비밀번호 인증 후 access(15m) + refresh(14d) 토큰 발급. BE-3: refresh는 httpOnly cookie로도 응답."
              + " body.refreshToken은 한시 호환용이며 FE-1 cleanup 후 제거된다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "JWT 응답 + Set-Cookie: refresh_token"),
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
  })
  @PostMapping("/auth/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    String email = request.email() != null ? request.email().trim() : "";
    String password = request.password() != null ? request.password() : "";
    String ip = clientIp(httpRequest);
    // BE-4.4: per-account 잠금 / per-IP throttling pre-check.
    lockoutService.preCheck(email, ip);
    try {
      authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    } catch (AuthenticationException ex) {
      lockoutService.recordFailure(email, ip);
      throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.", ex);
    }
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("회원 없음: " + email));
    // BE-4.6: legacy(BCrypt 등) 해시 → Argon2id rehash on login.
    if (passwordEncoder.upgradeEncoding(member.getPassword())) {
      member.changePassword(passwordEncoder.encode(password));
      memberRepository.save(member);
    }
    lockoutService.recordSuccess(email);
    LoginResponse body = authService.issueOnLogin(member);
    return withRefreshCookie(body);
  }

  @Operation(
      summary = "비밀번호 변경",
      description =
          "현재 비밀번호 확인 → 정책 검증 → encode + 저장 → revocation epoch bump + 현재 access denylist 추가."
              + " 다른 디바이스 토큰은 epoch 비교로 차단(BE-4.3).")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "변경 성공"),
    @ApiResponse(responseCode = "400", description = "정책 위반"),
    @ApiResponse(responseCode = "401", description = "인증 누락 또는 현재 비밀번호 불일치")
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/auth/password")
  public ResponseEntity<Void> changePassword(
      @Valid @RequestBody PasswordChangeRequest request,
      @AuthenticationPrincipal UserDetails userDetails,
      HttpServletRequest httpRequest) {
    if (userDetails == null) {
      throw new BadCredentialsException("인증이 필요합니다.");
    }
    Member member =
        memberRepository
            .findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("회원 없음: " + userDetails.getUsername()));
    String currentJti = (String) httpRequest.getAttribute(JwtAuthenticationFilter.ATTR_ACCESS_JTI);
    Object expAttr = httpRequest.getAttribute(JwtAuthenticationFilter.ATTR_ACCESS_EXPIRES_AT_EPOCH_SECOND);
    long ttlSec = expAttr instanceof Number n ? n.longValue() - java.time.Instant.now().getEpochSecond() : 0L;
    authService.changePassword(
        member, request.currentPassword(), request.newPassword(),
        passwordEncoder, passwordPolicy, currentJti, ttlSec);
    return ResponseEntity.status(HttpStatus.NO_CONTENT)
        .header(HttpHeaders.SET_COOKIE, authCookieService.clearRefresh().toString())
        .build();
  }

  @Operation(
      summary = "비밀번호 reset 시작",
      description =
          "이메일 입력 → 30분 1회용 토큰 발급. 응답은 사용자 존재 여부와 무관하게 200(enumeration 방지)."
              + " 학습 단계에선 콘솔 로그에 토큰을 출력한다.")
  @ApiResponse(responseCode = "200", description = "OK (사용자 존재 여부 노출 안 함)")
  @PostMapping("/auth/password-reset/initiate")
  public ResponseEntity<Map<String, Object>> resetInitiate(
      @Valid @RequestBody PasswordResetInitiateRequest request) {
    java.util.Optional<String> token = passwordResetService.initiate(request.email());
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("status", "ok");
    // 학습 단계: 토큰을 응답에도 동봉(production은 절대 금지). e2e 편의용.
    token.ifPresent(t -> body.put("debugToken", t));
    return ResponseEntity.ok(body);
  }

  @Operation(
      summary = "비밀번호 reset 완료",
      description = "토큰 검증(1회용) → 정책 검증 → encode + 저장 + revocation epoch bump.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "OK"),
    @ApiResponse(responseCode = "400", description = "토큰 무효 또는 정책 위반")
  })
  @PostMapping("/auth/password-reset/complete")
  public ResponseEntity<Void> resetComplete(@Valid @RequestBody PasswordResetCompleteRequest request) {
    passwordResetService.complete(request.token(), request.newPassword());
    return ResponseEntity.noContent().build();
  }

  /** X-Forwarded-For 우선, 없으면 remoteAddr. 학습 단계 단순 처리. */
  private static String clientIp(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      int comma = xff.indexOf(',');
      return (comma > 0 ? xff.substring(0, comma) : xff).trim();
    }
    return req.getRemoteAddr();
  }

  @Operation(
      summary = "Refresh rotation",
      description =
          "refresh token 검증 → 새 access + 새 refresh. BE-3: cookie {@code refresh_token} 우선, 없으면"
              + " body.refreshToken (deprecated). replay 감지 시 family 폐기 + cookie clear.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "새 토큰 쌍 + Set-Cookie 갱신"),
    @ApiResponse(responseCode = "400", description = "cookie/body 모두 부재"),
    @ApiResponse(responseCode = "401", description = "refresh 만료/서명 오류/replay 감지"),
    @ApiResponse(responseCode = "403", description = "CSRF token 누락 또는 불일치")
  })
  @PostMapping("/auth/refresh")
  public ResponseEntity<LoginResponse> refresh(
      @CookieValue(name = "refresh_token", required = false) String refreshCookie,
      @RequestBody(required = false) RefreshRequest request) {
    String token = pickRefresh(refreshCookie, request);
    if (token == null) {
      throw new RefreshTokenException("refresh token 누락 (cookie/body 모두 비어 있음)");
    }
    // 검증 실패/replay는 RefreshTokenException으로 GlobalExceptionHandler → 401. cookie 정리는 FE-1에서 401
    // 응답을 받은 뒤 logout 호출로 처리(또는 만료까지 둠). 본 endpoint에서 cookie clear 헤더를 동봉하지 않는 이유는
    // 401 응답에 Set-Cookie를 박으면 cookie repository와 응답 사이에 race가 생기고, 정상 흐름과 분기점이 두 갈래가
    // 되는 것을 피하기 위함이다.
    LoginResponse body = authService.rotate(token);
    return withRefreshCookie(body);
  }

  @Operation(
      summary = "로그아웃",
      description =
          "BE-3: cookie {@code refresh_token} 우선, 없으면 body.refreshToken. family 폐기 + 현재 access를"
              + " denylist에 추가. 응답엔 cookie clear 동봉.")
  @ApiResponse(responseCode = "204", description = "OK + Set-Cookie clear")
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "refresh_token", required = false) String refreshCookie,
      @RequestBody(required = false) LogoutRequest request,
      HttpServletRequest httpRequest) {
    String accessJti = (String) httpRequest.getAttribute(JwtAuthenticationFilter.ATTR_ACCESS_JTI);
    Object expiresAtAttr =
        httpRequest.getAttribute(JwtAuthenticationFilter.ATTR_ACCESS_EXPIRES_AT_EPOCH_SECOND);
    long ttlSeconds = 0;
    if (expiresAtAttr instanceof Number n) {
      ttlSeconds = n.longValue() - java.time.Instant.now().getEpochSecond();
    }

    String refreshToken =
        pickRefresh(
            refreshCookie, request != null ? new RefreshRequest(request.refreshToken()) : null);
    String familyId = null;
    if (refreshToken != null) {
      try {
        Claims claims = tokenProvider.parseRefreshToken(refreshToken);
        familyId = claims.get("family_id", String.class);
      } catch (JwtException ignore) {
        // 무효 refresh는 무시 — access denylist만 처리.
      }
    }
    authService.logout(accessJti, ttlSeconds, familyId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT)
        .header(HttpHeaders.SET_COOKIE, authCookieService.clearRefresh().toString())
        .build();
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

  /** cookie 우선 + body fallback — cookie 값이 비어 있지 않으면 cookie 사용. */
  private static String pickRefresh(String cookieValue, RefreshRequest body) {
    if (cookieValue != null && !cookieValue.isBlank()) {
      return cookieValue;
    }
    if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
      return body.refreshToken();
    }
    return null;
  }

  /** LoginResponse + Set-Cookie 헤더로 wrap. */
  private ResponseEntity<LoginResponse> withRefreshCookie(LoginResponse body) {
    ResponseCookie cookie =
        authCookieService.buildRefresh(body.refreshToken(), body.refreshExpiresInSeconds());
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
  }
}

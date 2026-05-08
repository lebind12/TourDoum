package com.ssafy.tourdoum.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 인증 필터 — `Authorization: Bearer <jwt>` 추출 → 검증 → SecurityContext 채움.
 *
 * <p>BE-1: 헤더 흐름. BE-2(#63): denylist 체크 + access claim을 request attribute로 expose (logout endpoint
 * 가 jti/expiresAt 사용).
 *
 * <p>cookie 추출은 BE-3에서 추가. 토큰 부재/검증 실패/denylist 매치 시 SecurityContext를 비워둔 채 통과시켜 후속 인가 단계가 401을
 * 반환하게 한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  /** request attribute key — controller에서 logout 시 jti/expiresAt 회수. */
  public static final String ATTR_ACCESS_JTI = "auth.access.jti";

  public static final String ATTR_ACCESS_EXPIRES_AT_EPOCH_SECOND =
      "auth.access.expiresAtEpochSecond";

  private final JwtTokenProvider tokenProvider;
  private final AccessTokenDenylist denylist;
  private final UserRevocationStore revocationStore;

  public JwtAuthenticationFilter(
      JwtTokenProvider tokenProvider,
      AccessTokenDenylist denylist,
      UserRevocationStore revocationStore) {
    this.tokenProvider = tokenProvider;
    this.denylist = denylist;
    this.revocationStore = revocationStore;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = extractBearer(request);
    if (token != null) {
      try {
        Claims claims = tokenProvider.parseAccessToken(token);
        String jti = claims.getId();
        Object uidClaim = claims.get("uid");
        long memberId = uidClaim instanceof Number n ? n.longValue() : -1L;
        long iatSec =
            claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant().getEpochSecond() : 0L;
        boolean denied = jti != null && denylist.contains(jti);
        // BE-4.3: revocation epoch — password 변경 시 bump된 epoch 이전 발급 토큰은 무효.
        boolean revoked = memberId > 0 && iatSec > 0 && iatSec < revocationStore.currentEpoch(memberId);
        if (denied || revoked) {
          SecurityContextHolder.clearContext();
        } else {
          Object roleClaim = claims.get("role");
          String role = roleClaim != null ? roleClaim.toString() : "ROLE_USER";
          UserDetails userDetails =
              User.withUsername(claims.getSubject())
                  .password("") // JWT 검증 후 의미 없음 — Spring 보안 모델 호환 placeholder
                  .authorities(List.of(new SimpleGrantedAuthority(role)))
                  .build();
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(
                  userDetails, null, userDetails.getAuthorities());
          SecurityContext context = SecurityContextHolder.createEmptyContext();
          context.setAuthentication(authentication);
          SecurityContextHolder.setContext(context);

          // logout endpoint가 jti/expiresAt을 회수하기 위해 request attribute로 expose.
          request.setAttribute(ATTR_ACCESS_JTI, jti);
          if (claims.getExpiration() != null) {
            request.setAttribute(
                ATTR_ACCESS_EXPIRES_AT_EPOCH_SECOND,
                claims.getExpiration().toInstant().getEpochSecond());
          }
        }
      } catch (JwtException ex) {
        // 무효 토큰 → SecurityContext 비움 → 후속 AuthorizationFilter가 401 반환.
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }

  private static String extractBearer(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
      String value = header.substring(BEARER_PREFIX.length()).trim();
      return value.isEmpty() ? null : value;
    }
    return null;
  }
}

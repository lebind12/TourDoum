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
 * <p>ADR-0011 BE-1: 헤더 흐름만 처리. cookie 추출은 BE-3에서 추가. 토큰 부재/검증 실패 시 SecurityContext를 비워둔 채 통과시켜 후속
 * 인가 단계({@code AuthorizationFilter})가 401을 반환하게 한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider tokenProvider;

  public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
    this.tokenProvider = tokenProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = extractBearer(request);
    if (token != null) {
      try {
        Claims claims = tokenProvider.parseAccessToken(token);
        Object roleClaim = claims.get("role");
        String role = roleClaim != null ? roleClaim.toString() : "ROLE_USER";
        UserDetails userDetails =
            User.withUsername(claims.getSubject())
                .password("") // password는 JWT 검증 후 의미 없음 — Spring 보안 모델 호환용 placeholder
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .build();
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
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

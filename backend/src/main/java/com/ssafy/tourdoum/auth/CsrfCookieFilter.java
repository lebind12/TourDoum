package com.ssafy.tourdoum.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF cookie materialize 필터 — XSRF-TOKEN cookie를 매 응답에 박는다 (ADR-0011 BE-3).
 *
 * <p>Spring Security 6의 {@code CsrfTokenRequestAttributeHandler}는 deferred token을 사용하므로 누군가 {@code
 * csrfToken.getToken()}을 호출하기 전엔 cookie repository에 저장이 일어나지 않는다. 그러면 SPA 첫 로딩 시점에 XSRF-TOKEN
 * cookie가 비어 있어 mutation 요청이 403으로 막힌다. 본 필터가 매 요청에서 token을 강제 materialize해 cookie가 항상 응답에 포함되도록
 * 한다.
 *
 * <p>참고: <a
 * href="https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#servlet-csrf-integration-javascript-spa">Spring
 * Security CSRF SPA integration</a>.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (token != null) {
      // getToken() 호출이 token을 materialize해 CookieCsrfTokenRepository.saveToken을 트리거한다.
      token.getToken();
    }
    chain.doFilter(request, response);
  }
}

package com.ssafy.tourdoum.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRole;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/** JwtAuthenticationFilter 단위 테스트 — bearer 흐름 검증. */
class JwtAuthenticationFilterTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private JwtTokenProvider provider() throws Exception {
    JwtProperties p =
        new JwtProperties(
            "tourdoum",
            Duration.ofMinutes(15),
            "dev-1",
            null,
            "classpath:keys/jwt-dev-private.pem",
            "classpath:keys/jwt-dev-public.pem",
            null);
    return new JwtTokenProvider(p, new DefaultResourceLoader());
  }

  private Member fakeMember() {
    Member m = Mockito.mock(Member.class);
    given(m.getId()).willReturn(7L);
    given(m.getEmail()).willReturn("u@example.com");
    given(m.getRole()).willReturn(MemberRole.ROLE_USER);
    return m;
  }

  @Test
  @DisplayName("Bearer 유효 토큰 → SecurityContext에 UserDetails 채움")
  void valid_bearer_populates_context() throws Exception {
    JwtTokenProvider tp = provider();
    String token = tp.issueAccessToken(fakeMember()).token();
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tp);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = Mockito.mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getPrincipal()).isInstanceOf(UserDetails.class);
    UserDetails ud = (UserDetails) auth.getPrincipal();
    assertThat(ud.getUsername()).isEqualTo("u@example.com");
    assertThat(ud.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    Mockito.verify(chain).doFilter(req, res);
  }

  @Test
  @DisplayName("Authorization 헤더 부재 → SecurityContext 비어 있음 + chain 통과")
  void missing_header_passes_through() throws Exception {
    JwtTokenProvider tp = provider();
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tp);

    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = Mockito.mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    Mockito.verify(chain).doFilter(req, res);
  }

  @Test
  @DisplayName("Bearer 무효 토큰 → context 비움 + chain 통과 (후속 401은 인가 단계)")
  void invalid_bearer_clears_context() throws Exception {
    JwtTokenProvider tp = provider();
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tp);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer not-a-real-jwt");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = Mockito.mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    Mockito.verify(chain).doFilter(req, res);
  }
}

package com.ssafy.tourdoum.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import com.ssafy.tourdoum.member.MemberRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * BE-3 cookie + CSRF 흐름 슬라이스 — H2 + InMemory store/denylist.
 *
 * <ul>
 *   <li>login 응답에 {@code Set-Cookie: refresh_token} 동봉
 *   <li>refresh가 cookie path만으로 동작 (body 없음)
 *   <li>cookie + body 동시 → cookie 우선
 *   <li>logout 응답에 cookie clear (Max-Age=0) 동봉
 *   <li>CSRF token 누락 시 refresh/logout 403
 *   <li>login은 CSRF 면제 (token 없이 200)
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryAuthTestConfig.class)
class AuthCookieFlowTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RefreshTokenStore refreshTokenStore;
  @Autowired private AccessTokenDenylist accessTokenDenylist;

  private static final String EMAIL = "cookie@example.com";
  private static final String PASSWORD = "password123";

  @BeforeEach
  void seedMember() {
    memberRepository
        .findByEmail(EMAIL)
        .ifPresentOrElse(
            m -> {},
            () ->
                memberRepository.save(
                    Member.builder()
                        .email(EMAIL)
                        .password(passwordEncoder.encode(PASSWORD))
                        .nickname("cookieuser")
                        .role(MemberRole.ROLE_USER)
                        .build()));
    if (refreshTokenStore instanceof InMemoryRefreshTokenStore mem) {
      mem.clear();
    }
    if (accessTokenDenylist instanceof InMemoryAccessTokenDenylist mem) {
      mem.clear();
    }
  }

  private MvcResult performLogin() throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"%s"}
                    """
                        .formatted(EMAIL, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  @DisplayName("login 응답에 Set-Cookie: refresh_token (httpOnly + Path=/api/auth + SameSite=Strict)")
  void login_sets_refresh_cookie() throws Exception {
    MvcResult result = performLogin();
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).as("Set-Cookie 헤더").isNotNull();
    assertThat(setCookie).contains("refresh_token=");
    assertThat(setCookie).contains("HttpOnly");
    assertThat(setCookie).contains("Path=/api/auth");
    assertThat(setCookie).contains("SameSite=Strict");

    // body의 refreshToken과 cookie 값이 동일해야 한다 (한시 호환).
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    String bodyRefresh = body.get("refreshToken").asText();
    Cookie cookie = result.getResponse().getCookie("refresh_token");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isEqualTo(bodyRefresh);
  }

  @Test
  @DisplayName("refresh가 cookie 단독으로 동작 (body 비어 있음)")
  void refresh_via_cookie_only() throws Exception {
    MvcResult login = performLogin();
    Cookie refreshCookie = login.getResponse().getCookie("refresh_token");
    assertThat(refreshCookie).isNotNull();

    mockMvc
        .perform(post("/api/auth/refresh").with(csrf()).cookie(refreshCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty());
  }

  @Test
  @DisplayName("refresh — cookie + body 동시일 때 cookie 우선 (body는 stale 값)")
  void refresh_cookie_takes_precedence_over_body() throws Exception {
    MvcResult login = performLogin();
    Cookie refreshCookie = login.getResponse().getCookie("refresh_token");
    String validRefresh = refreshCookie.getValue();
    String staleBody = "stale.body.token";

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .with(csrf())
                .cookie(new Cookie("refresh_token", validRefresh))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + staleBody + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("logout 응답에 Set-Cookie: refresh_token=; Max-Age=0 (clear)")
  void logout_clears_refresh_cookie() throws Exception {
    MvcResult login = performLogin();
    JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
    String access = body.get("accessToken").asText();
    Cookie refreshCookie = login.getResponse().getCookie("refresh_token");

    MvcResult logoutResult =
        mockMvc
            .perform(
                post("/api/auth/logout")
                    .with(csrf())
                    .cookie(refreshCookie)
                    .header("Authorization", "Bearer " + access))
            .andExpect(status().isNoContent())
            .andReturn();
    String setCookie = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).as("logout Set-Cookie").isNotNull();
    assertThat(setCookie).contains("refresh_token=");
    assertThat(setCookie).contains("Max-Age=0");
  }

  @Test
  @DisplayName("refresh — CSRF token 누락 시 403")
  void refresh_without_csrf_returns_403() throws Exception {
    MvcResult login = performLogin();
    Cookie refreshCookie = login.getResponse().getCookie("refresh_token");

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshCookie))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("logout — CSRF token 누락 시 403 (Bearer 있어도)")
  void logout_without_csrf_returns_403() throws Exception {
    MvcResult login = performLogin();
    JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
    String access = body.get("accessToken").asText();
    Cookie refreshCookie = login.getResponse().getCookie("refresh_token");

    mockMvc
        .perform(
            post("/api/auth/logout")
                .cookie(refreshCookie)
                .header("Authorization", "Bearer " + access))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("login은 CSRF 면제 (token 없이도 200)")
  void login_is_exempt_from_csrf() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"%s"}
                    """
                        .formatted(EMAIL, PASSWORD)))
        .andExpect(status().isOk());
  }

  // XSRF-TOKEN cookie materialize 검증은 MockMvc 환경에서 신뢰할 수 없다 — `with(csrf())` test
  // post-processor가 SpringSecurity의 CsrfTokenRepository를 TestCsrfTokenRepository로 래핑하므로 일반
  // login 응답에서 cookie save가 일어나지 않을 수 있다. 실제 브라우저 흐름은 QA-1 Playwright e2e에서
  // 검증한다(FE-1 보강 task에서 추가 예정). 본 슬라이스에서는 CSRF 강제 자체가 통과/거부되는지로 충분.
}

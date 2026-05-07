package com.ssafy.tourdoum.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import com.ssafy.tourdoum.member.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Auth JWT 통합 — H2 + InMemory store/denylist (Docker/Redis 불필요).
 *
 * <p>BE-1: login + bearer me<br>
 * BE-2(#63): refresh rotation + replay 감지 + logout 정식 (family revoke + denylist).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InMemoryAuthTestConfig.class)
class AuthControllerJwtTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RefreshTokenStore refreshTokenStore;
  @Autowired private AccessTokenDenylist accessTokenDenylist;

  private static final String EMAIL = "jwt@example.com";
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
                        .nickname("jwtuser")
                        .role(MemberRole.ROLE_USER)
                        .build()));
    if (refreshTokenStore instanceof InMemoryRefreshTokenStore mem) {
      mem.clear();
    }
    if (accessTokenDenylist instanceof InMemoryAccessTokenDenylist mem) {
      mem.clear();
    }
  }

  private LoginResponseTokens login() throws Exception {
    MvcResult result =
        mockMvc
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
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return new LoginResponseTokens(
        body.get("accessToken").asText(), body.get("refreshToken").asText());
  }

  @Test
  @DisplayName("로그인 → access + refresh 응답 + Bearer로 /api/me 200")
  void login_returns_access_and_refresh() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"%s"}
                    """
                        .formatted(EMAIL, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresInSeconds").value(900))
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshExpiresInSeconds").value(14L * 24 * 60 * 60))
        .andExpect(jsonPath("$.user.email").value(EMAIL))
        .andExpect(jsonPath("$.user.role").value("ROLE_USER"));
  }

  @Test
  @DisplayName("refresh rotation — 새 access + 새 refresh + family Redis 갱신")
  void refresh_rotation_works() throws Exception {
    LoginResponseTokens initial = login();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"refreshToken":"%s"}
                        """
                            .formatted(initial.refreshToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("refreshToken").asText()).isNotEqualTo(initial.refreshToken());
    // 새 access로도 /api/me 200
    mockMvc
        .perform(
            get("/api/me").header("Authorization", "Bearer " + body.get("accessToken").asText()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("refresh replay (이미 회전된 옛 refresh를 두 번째로 재사용) → 401 + family 폐기")
  void refresh_replay_revokes_family() throws Exception {
    LoginResponseTokens initial = login();
    // 첫 회전: 정상 (current 사용)
    MvcResult firstRotate =
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"refreshToken":"%s"}
                        """
                            .formatted(initial.refreshToken())))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode firstRotated = objectMapper.readTree(firstRotate.getResponse().getContentAsString());

    // 두 번째 회전 (current가 prev로 내려간 상태에서 prev 사용 — grace 허용)
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken":"%s"}
                    """
                        .formatted(firstRotated.get("refreshToken").asText())))
        .andExpect(status().isOk());

    // 이제 initial.refreshToken은 더 이상 prev도 current도 아님 → replay 감지 → 401
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken":"%s"}
                    """
                        .formatted(initial.refreshToken())))
        .andExpect(status().isUnauthorized());

    // family 폐기 확인 — 다시 회전 시도해도 401 (family 미존재)
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken":"%s"}
                    """
                        .formatted(firstRotated.get("refreshToken").asText())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("logout 후 같은 access로 /api/me 401 (denylist)")
  void logout_blocks_access_via_denylist() throws Exception {
    LoginResponseTokens tokens = login();

    // logout — refresh 동봉 → family revoke + access denylist
    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken":"%s"}
                    """
                        .formatted(tokens.refreshToken())))
        .andExpect(status().isNoContent());

    // 같은 access로 /api/me → 401 (filter denylist hit)
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + tokens.accessToken()))
        .andExpect(status().isUnauthorized());

    // 같은 refresh로 회전 시도 → 401 (family 폐기됨)
    mockMvc
        .perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"refreshToken":"%s"}
                    """
                        .formatted(tokens.refreshToken())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("logout — Bearer 없으면 401 (인증 필요)")
  void logout_without_bearer_returns_401() throws Exception {
    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("잘못된 비밀번호 → 401")
  void login_with_wrong_password_returns_401() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"wrong"}
                    """
                        .formatted(EMAIL)))
        .andExpect(status().isUnauthorized());
  }

  /** 짧은 record — 토큰 쌍 캐리어. */
  private record LoginResponseTokens(String accessToken, String refreshToken) {}
}

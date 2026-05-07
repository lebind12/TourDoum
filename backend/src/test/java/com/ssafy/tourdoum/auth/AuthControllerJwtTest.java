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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Auth JWT 통합 — H2 + 컨텍스트 풀로딩 (Docker 불필요).
 *
 * <p>ADR-0011 BE-1 응답 contract: {@code POST /api/auth/login} → {@code {accessToken,
 * expiresInSeconds, tokenType, user}}, {@code Authorization: Bearer ...}로 {@code GET /api/me} 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerJwtTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper objectMapper;

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
  }

  @Test
  @DisplayName("로그인 → JWT 응답 + Bearer로 /api/me 200")
  void login_returns_jwt_and_me_works_with_bearer() throws Exception {
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
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresInSeconds").value(900))
            .andExpect(jsonPath("$.user.email").value(EMAIL))
            .andExpect(jsonPath("$.user.role").value("ROLE_USER"))
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    String token = body.get("accessToken").asText();
    assertThat(token).isNotBlank();

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.role").value("ROLE_USER"));
  }

  @Test
  @DisplayName("Bearer 없으면 /api/me 401")
  void me_without_bearer_returns_401() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
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

  @Test
  @DisplayName("logout — 204 stub (BE-1는 서버 상태 없음)")
  void logout_returns_204() throws Exception {
    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
  }
}

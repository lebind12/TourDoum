package com.ssafy.tourdoum.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.DefaultResourceLoader;

/** JwtTokenProvider refresh + previous-kid 검증 (#63 BE-2). */
class JwtTokenProviderRefreshTest {

  private JwtProperties props() {
    return new JwtProperties(
        "tourdoum",
        Duration.ofMinutes(15),
        Duration.ofDays(14),
        "dev-1",
        null,
        "classpath:keys/jwt-dev-private.pem",
        "classpath:keys/jwt-dev-public.pem",
        null);
  }

  private Member member() {
    Member m = Mockito.mock(Member.class);
    Mockito.when(m.getId()).thenReturn(1L);
    Mockito.when(m.getEmail()).thenReturn("u@example.com");
    Mockito.when(m.getRole()).thenReturn(MemberRole.ROLE_USER);
    return m;
  }

  @Test
  @DisplayName("issueRefreshToken + parseRefreshToken — family_id/jti/type 클레임 라운드트립")
  void refresh_roundtrip() throws IOException {
    JwtTokenProvider provider = new JwtTokenProvider(props(), new DefaultResourceLoader());
    String familyId = UUID.randomUUID().toString();
    String jti = UUID.randomUUID().toString();
    JwtTokenProvider.IssuedToken token = provider.issueRefreshToken(member(), familyId, jti);
    assertThat(token.expiresInSeconds()).isEqualTo(14L * 24 * 60 * 60);

    Claims claims = provider.parseRefreshToken(token.token());
    assertThat(claims.get("type")).isEqualTo("refresh");
    assertThat(claims.get("family_id")).isEqualTo(familyId);
    assertThat(claims.getId()).isEqualTo(jti);
    assertThat(claims.getSubject()).isEqualTo("u@example.com");
  }

  @Test
  @DisplayName("parseRefreshToken — type=access 토큰을 refresh로 parse 시 JwtException")
  void parse_access_as_refresh_rejected() throws IOException {
    JwtTokenProvider provider = new JwtTokenProvider(props(), new DefaultResourceLoader());
    String access = provider.issueAccessToken(member()).token();
    assertThatThrownBy(() -> provider.parseRefreshToken(access)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("previous-kid 활성 — 동일 PEM을 previous로도 등록하면 검증 호환 (회전 학습 demo)")
  void previous_kid_verification() throws IOException {
    // dev에서 별도 previous PEM이 없으므로 동일 PEM을 previous-kid로도 등록 — 검증 호환만 확인.
    JwtProperties p =
        new JwtProperties(
            "tourdoum",
            Duration.ofMinutes(15),
            Duration.ofDays(14),
            "dev-1",
            "dev-0",
            "classpath:keys/jwt-dev-private.pem",
            "classpath:keys/jwt-dev-public.pem",
            "classpath:keys/jwt-dev-public.pem");
    JwtTokenProvider provider = new JwtTokenProvider(p, new DefaultResourceLoader());
    String token = provider.issueAccessToken(member()).token();
    Claims claims = provider.parseAccessToken(token);
    assertThat(claims.getSubject()).isEqualTo("u@example.com");
  }
}

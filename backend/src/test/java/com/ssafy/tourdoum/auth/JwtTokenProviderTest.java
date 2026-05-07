package com.ssafy.tourdoum.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

/** JwtTokenProvider 단위 테스트 — RS256 + classpath dev key. */
class JwtTokenProviderTest {

  private static final ResourceLoader LOADER = new DefaultResourceLoader();

  private JwtProperties props(Duration ttl) {
    return new JwtProperties(
        "tourdoum",
        ttl,
        "dev-1",
        null,
        "classpath:keys/jwt-dev-private.pem",
        "classpath:keys/jwt-dev-public.pem",
        null);
  }

  private Member fakeMember() {
    Member m = mock(Member.class);
    when(m.getId()).thenReturn(42L);
    when(m.getEmail()).thenReturn("u@example.com");
    when(m.getRole()).thenReturn(MemberRole.ROLE_USER);
    return m;
  }

  @Test
  @DisplayName("issue + parse — sub/uid/role/iss 클레임이 정상 라운드트립")
  void issue_then_parse_roundtrip() throws Exception {
    JwtTokenProvider provider = new JwtTokenProvider(props(Duration.ofMinutes(15)), LOADER);
    JwtTokenProvider.IssuedToken token = provider.issueAccessToken(fakeMember());
    assertThat(token.token()).isNotBlank();
    assertThat(token.expiresInSeconds()).isEqualTo(15 * 60L);

    Claims claims = provider.parseAccessToken(token.token());
    assertThat(claims.getSubject()).isEqualTo("u@example.com");
    assertThat(claims.get("uid", Number.class).longValue()).isEqualTo(42L);
    assertThat(claims.get("role", String.class)).isEqualTo("ROLE_USER");
    assertThat(claims.getIssuer()).isEqualTo("tourdoum");
    assertThat(claims.getId()).isNotBlank(); // jti
  }

  @Test
  @DisplayName("expired — 만료된 토큰 parse 시 ExpiredJwtException")
  void expired_token_throws() throws Exception {
    Clock past = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
    JwtTokenProvider issuingProvider =
        new JwtTokenProvider(props(Duration.ofMinutes(15)), LOADER, past);
    String token = issuingProvider.issueAccessToken(fakeMember()).token();

    JwtTokenProvider verifyingProvider =
        new JwtTokenProvider(props(Duration.ofMinutes(15)), LOADER, Clock.systemUTC());
    assertThatThrownBy(() -> verifyingProvider.parseAccessToken(token))
        .isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  @DisplayName("tampered signature — 변조 시 JwtException")
  void tampered_token_rejected() throws Exception {
    JwtTokenProvider provider = new JwtTokenProvider(props(Duration.ofMinutes(15)), LOADER);
    String token = provider.issueAccessToken(fakeMember()).token();
    // payload(중간 segment)에 1글자 변조 → 서명 mismatch
    String[] parts = token.split("\\.");
    char first = parts[1].charAt(0);
    char swapped = (first == 'A') ? 'B' : 'A';
    parts[1] = swapped + parts[1].substring(1);
    String tampered = String.join(".", parts);
    assertThatThrownBy(() -> provider.parseAccessToken(tampered))
        .isInstanceOf(io.jsonwebtoken.JwtException.class);
  }
}

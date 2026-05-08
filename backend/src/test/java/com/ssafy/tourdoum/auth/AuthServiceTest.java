package com.ssafy.tourdoum.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import com.ssafy.tourdoum.member.MemberRole;
import io.jsonwebtoken.Claims;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * AuthService 단위 테스트 — rotation / replay / logout (#63 BE-2).
 *
 * <p>실제 JwtTokenProvider 사용(RS256 + classpath dev key) + InMemory store/denylist.
 */
class AuthServiceTest {

  private JwtProperties properties;
  private JwtTokenProvider provider;
  private MemberRepository memberRepository;
  private InMemoryRefreshTokenStore store;
  private InMemoryAccessTokenDenylist denylist;
  private InMemoryUserRevocationStore revocationStore;
  private AuthService service;
  private Member member;

  @BeforeEach
  void setUp() throws IOException {
    properties =
        new JwtProperties(
            "tourdoum",
            Duration.ofMinutes(15),
            Duration.ofDays(14),
            "dev-1",
            null,
            "classpath:keys/jwt-dev-private.pem",
            "classpath:keys/jwt-dev-public.pem",
            null);
    provider = new JwtTokenProvider(properties, new DefaultResourceLoader());
    memberRepository = Mockito.mock(MemberRepository.class);
    store = new InMemoryRefreshTokenStore();
    denylist = new InMemoryAccessTokenDenylist();
    revocationStore = new InMemoryUserRevocationStore();
    service =
        new AuthService(
            provider,
            properties,
            memberRepository,
            store,
            denylist,
            revocationStore,
            Clock.systemUTC());

    member = Mockito.mock(Member.class);
    Mockito.when(member.getId()).thenReturn(11L);
    Mockito.when(member.getEmail()).thenReturn("a@example.com");
    Mockito.when(member.getNickname()).thenReturn("alice");
    Mockito.when(member.getRole()).thenReturn(MemberRole.ROLE_USER);
    Mockito.when(memberRepository.findById(11L)).thenReturn(Optional.of(member));
  }

  @Test
  @DisplayName("issueOnLogin — family 박제 + access/refresh 발급")
  void issueOnLogin_creates_family_and_tokens() {
    LoginResponse resp = service.issueOnLogin(member);
    assertThat(resp.accessToken()).isNotBlank();
    assertThat(resp.refreshToken()).isNotBlank();
    Claims refreshClaims = provider.parseRefreshToken(resp.refreshToken());
    String familyId = refreshClaims.get("family_id", String.class);
    assertThat(store.find(familyId)).isPresent();
  }

  @Test
  @DisplayName("rotate happy — currentJti로 회전 시 새 토큰 + family 갱신")
  void rotate_happy() {
    LoginResponse first = service.issueOnLogin(member);
    LoginResponse second = service.rotate(first.refreshToken());

    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    Claims newRefresh = provider.parseRefreshToken(second.refreshToken());
    String familyId = newRefresh.get("family_id", String.class);
    assertThat(store.find(familyId)).isPresent();
    // 이전 access는 denylist에 추가되어야 함
    Claims oldAccess = provider.parseAccessToken(first.accessToken());
    assertThat(denylist.contains(oldAccess.getId())).isTrue();
  }

  @Test
  @DisplayName("rotate prev grace — 직전 회전 후 prev로 한 번 더 회전 가능 (network race)")
  void rotate_prev_grace() {
    LoginResponse first = service.issueOnLogin(member);
    LoginResponse second = service.rotate(first.refreshToken()); // 정상
    // 이제 second가 current, first가 prev (family.withRotation 결과)
    // first.refreshToken은 prevJti에 해당 → grace 허용 → 정상 회전
    LoginResponse retried = service.rotate(first.refreshToken());
    assertThat(retried.refreshToken()).isNotBlank();
  }

  @Test
  @DisplayName("rotate replay — current/prev 둘 다 아닌 jti → family 폐기 + 401 exception")
  void rotate_replay_revokes_family() {
    LoginResponse first = service.issueOnLogin(member);
    LoginResponse second = service.rotate(first.refreshToken()); // current=second, prev=first
    LoginResponse third = service.rotate(second.refreshToken()); // current=third, prev=second
    // 이제 first.refreshToken은 current도 prev도 아님 → replay
    String familyId =
        provider.parseRefreshToken(first.refreshToken()).get("family_id", String.class);
    assertThatThrownBy(() -> service.rotate(first.refreshToken()))
        .isInstanceOf(RefreshTokenException.class);
    assertThat(store.find(familyId)).isEmpty(); // family 폐기됨
  }

  @Test
  @DisplayName("logout — family delete + access denylist add")
  void logout_revokes() {
    LoginResponse first = service.issueOnLogin(member);
    Claims access = provider.parseAccessToken(first.accessToken());
    Claims refresh = provider.parseRefreshToken(first.refreshToken());
    String familyId = refresh.get("family_id", String.class);
    long ttl =
        access.getExpiration().toInstant().getEpochSecond()
            - java.time.Instant.now().getEpochSecond();

    service.logout(access.getId(), ttl, familyId);

    assertThat(store.find(familyId)).isEmpty();
    assertThat(denylist.contains(access.getId())).isTrue();
  }
}

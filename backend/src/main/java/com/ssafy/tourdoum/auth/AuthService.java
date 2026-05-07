package com.ssafy.tourdoum.auth;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 비즈니스 로직 — login 발급, refresh rotation, logout family 폐기 (#63, ADR-0011 BE-2).
 *
 * <p>Controller에서 분리한 이유: rotation/family/denylist 흐름이 길어 단위 테스트가 까다로움. Service에서 Mock
 * store/denylist 로 검증한다.
 */
@Service
@Transactional
public class AuthService {

  private final JwtTokenProvider tokenProvider;
  private final JwtProperties properties;
  private final MemberRepository memberRepository;
  private final RefreshTokenStore refreshStore;
  private final AccessTokenDenylist denylist;
  private final Clock clock;

  @Autowired
  public AuthService(
      JwtTokenProvider tokenProvider,
      JwtProperties properties,
      MemberRepository memberRepository,
      RefreshTokenStore refreshStore,
      AccessTokenDenylist denylist) {
    this(tokenProvider, properties, memberRepository, refreshStore, denylist, Clock.systemUTC());
  }

  /** 테스트 친화 — Clock 주입. */
  public AuthService(
      JwtTokenProvider tokenProvider,
      JwtProperties properties,
      MemberRepository memberRepository,
      RefreshTokenStore refreshStore,
      AccessTokenDenylist denylist,
      Clock clock) {
    this.tokenProvider = tokenProvider;
    this.properties = properties;
    this.memberRepository = memberRepository;
    this.refreshStore = refreshStore;
    this.denylist = denylist;
    this.clock = clock;
  }

  /** 새 family + 발급 (login). */
  public LoginResponse issueOnLogin(Member member) {
    String familyId = UUID.randomUUID().toString();
    String accessJti = UUID.randomUUID().toString();
    String refreshJti = UUID.randomUUID().toString();

    JwtTokenProvider.IssuedToken access = tokenProvider.issueAccessToken(member, accessJti);
    JwtTokenProvider.IssuedToken refresh =
        tokenProvider.issueRefreshToken(member, familyId, refreshJti);

    Instant now = clock.instant();
    refreshStore.save(
        new RefreshTokenFamily(
            familyId,
            member.getId(),
            refreshJti,
            null,
            accessJti,
            now,
            now.plus(properties.refreshTtl())));
    return LoginResponse.of(access, refresh, MeResponse.from(member));
  }

  /**
   * Refresh rotation — body의 refresh를 검증, family 조회, replay 감지 시 폐기. 정상이면 새 access + 새 refresh 발급 +
   * family 갱신.
   */
  public LoginResponse rotate(String refreshToken) {
    Claims claims;
    try {
      claims = tokenProvider.parseRefreshToken(refreshToken);
    } catch (JwtException e) {
      throw new RefreshTokenException("refresh token 검증 실패", e);
    }

    String familyId = claims.get("family_id", String.class);
    String incomingJti = claims.getId();
    Long userId = ((Number) claims.get("uid")).longValue();

    if (familyId == null || incomingJti == null) {
      throw new RefreshTokenException("refresh token에 family_id/jti 누락");
    }

    RefreshTokenFamily family =
        refreshStore
            .find(familyId)
            .orElseThrow(() -> new RefreshTokenException("알 수 없는 family 또는 폐기됨: " + familyId));

    boolean current = incomingJti.equals(family.currentJti());
    boolean prev = incomingJti.equals(family.prevJti());

    if (!current && !prev) {
      // 재사용 탐지 — family 전체 폐기 + 현재 access denylist 추가.
      refreshStore.delete(familyId);
      denylistAccess(family.currentAccessJti(), claims.getExpiration().toInstant());
      throw new RefreshTokenException("refresh token replay 감지 — family 폐기됨");
    }

    Member member =
        memberRepository
            .findById(userId)
            .orElseThrow(() -> new UsernameNotFoundException("회원 없음: id=" + userId));

    String newAccessJti = UUID.randomUUID().toString();
    String newRefreshJti = UUID.randomUUID().toString();

    // 이전 access는 즉시 denylist 처리 (회전 직후 옛 access 무효화).
    denylistAccess(family.currentAccessJti(), claims.getExpiration().toInstant());

    JwtTokenProvider.IssuedToken access = tokenProvider.issueAccessToken(member, newAccessJti);
    JwtTokenProvider.IssuedToken refresh =
        tokenProvider.issueRefreshToken(member, familyId, newRefreshJti);

    refreshStore.save(family.withRotation(newRefreshJti, newAccessJti));
    return LoginResponse.of(access, refresh, MeResponse.from(member));
  }

  /**
   * Logout — family 전체 폐기 + 현재 access jti denylist. accessJti는 SecurityContext의 access claim에서 가져온
   * 값이며, 여기선 단순 박제.
   */
  public void logout(String accessJti, long ttlSeconds, String familyIdOrNull) {
    if (familyIdOrNull != null) {
      refreshStore.delete(familyIdOrNull);
    }
    if (accessJti != null) {
      denylist.add(accessJti, Math.max(ttlSeconds, 0));
    }
  }

  private void denylistAccess(String accessJti, Instant expiresAt) {
    if (accessJti == null) {
      return;
    }
    long ttlSeconds =
        expiresAt == null ? 0 : (expiresAt.getEpochSecond() - clock.instant().getEpochSecond());
    denylist.add(accessJti, Math.max(ttlSeconds, 0));
  }
}

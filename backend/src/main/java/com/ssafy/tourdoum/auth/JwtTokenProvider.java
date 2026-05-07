package com.ssafy.tourdoum.auth;

import com.ssafy.tourdoum.member.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.io.Decoders;
import java.io.IOException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * JWT 발급 + 검증 — RS256 + kid 기반 key 선택.
 *
 * <p>ADR-0011 BE-1 골격. active key 1개 + previous key 검증 허용 자리(map).
 * refresh/rotation/family/denylist는 BE-2 범위.
 *
 * <p>발급 토큰 구조: header={alg:RS256, kid, typ:JWT} / payload={sub:email, uid, role, iss, iat, exp,
 * jti}.
 */
@Component
public class JwtTokenProvider {

  private final JwtProperties properties;
  private final Clock clock;
  private final PrivateKey signingKey;
  private final Map<String, PublicKey> verificationKeys;

  @Autowired
  public JwtTokenProvider(JwtProperties properties, ResourceLoader resourceLoader)
      throws IOException {
    this(properties, resourceLoader, Clock.systemUTC());
  }

  /** 테스트 친화 — Clock 주입 가능. */
  public JwtTokenProvider(JwtProperties properties, ResourceLoader resourceLoader, Clock clock)
      throws IOException {
    this.properties = properties;
    this.clock = clock;
    this.signingKey = loadPrivateKey(resourceLoader, properties.privateKeyLocation());
    this.verificationKeys = new HashMap<>();
    this.verificationKeys.put(
        properties.activeKid(), loadPublicKey(resourceLoader, properties.publicKeyLocation()));
    if (StringUtils.hasText(properties.previousKid())
        && StringUtils.hasText(properties.previousPublicKeyLocation())) {
      this.verificationKeys.put(
          properties.previousKid(),
          loadPublicKey(resourceLoader, properties.previousPublicKeyLocation()));
    }
  }

  /** access token 발급. {@link Member}가 가진 식별 정보로 클레임 채움. */
  public IssuedToken issueAccessToken(Member member) {
    return issueAccessToken(member, UUID.randomUUID().toString());
  }

  /**
   * access token 발급 — jti 명시 (#63 BE-2). family rotation 시 호출자가 jti를 control 한다(같은 family에서 새
   * access의 jti를 family에 박제하기 위함).
   */
  public IssuedToken issueAccessToken(Member member, String jti) {
    Instant now = clock.instant();
    Instant exp = now.plus(properties.accessTtl());
    String token =
        Jwts.builder()
            .header()
            .add("kid", properties.activeKid())
            .add("typ", "JWT")
            .and()
            .issuer(properties.issuer())
            .subject(member.getEmail())
            .claim("uid", member.getId())
            .claim("role", member.getRole().name())
            .claim("type", "access")
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(signingKey, Jwts.SIG.RS256)
            .compact();
    return new IssuedToken(token, properties.accessTtl().toSeconds());
  }

  /**
   * refresh token 발급 — RS256 동일 key, ttl=refreshTtl, claim에 family_id/jti/type=refresh (#63 BE-2).
   */
  public IssuedToken issueRefreshToken(Member member, String familyId, String jti) {
    Instant now = clock.instant();
    Instant exp = now.plus(properties.refreshTtl());
    String token =
        Jwts.builder()
            .header()
            .add("kid", properties.activeKid())
            .add("typ", "refresh+JWT")
            .and()
            .issuer(properties.issuer())
            .subject(member.getEmail())
            .claim("uid", member.getId())
            .claim("role", member.getRole().name())
            .claim("type", "refresh")
            .claim("family_id", familyId)
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(signingKey, Jwts.SIG.RS256)
            .compact();
    return new IssuedToken(token, properties.refreshTtl().toSeconds());
  }

  /** access token 검증 + 클레임 반환. 만료/서명 오류 시 {@link io.jsonwebtoken.JwtException}. */
  public Claims parseAccessToken(String token) {
    Jws<Claims> jws =
        Jwts.parser()
            .keyLocator(new KidKeyLocator(verificationKeys))
            .requireIssuer(properties.issuer())
            .clock(() -> Date.from(clock.instant()))
            .build()
            .parseSignedClaims(token);
    return jws.getPayload();
  }

  /**
   * refresh token 검증 + 클레임 반환. 만료/서명 오류 또는 type ≠ "refresh"이면 {@link io.jsonwebtoken.JwtException}.
   */
  public Claims parseRefreshToken(String token) {
    Claims claims = parseAccessToken(token);
    Object type = claims.get("type");
    if (!"refresh".equals(type)) {
      throw new io.jsonwebtoken.JwtException("type 클레임이 refresh가 아님: " + type);
    }
    return claims;
  }

  // ── PEM loader ──
  private static PrivateKey loadPrivateKey(ResourceLoader loader, String location)
      throws IOException {
    String pem = readResource(loader, location);
    String body = stripPem(pem);
    byte[] der = Decoders.BASE64.decode(body);
    try {
      KeyFactory kf = KeyFactory.getInstance("RSA");
      return kf.generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalStateException("private key 로드 실패: " + location, e);
    }
  }

  private static PublicKey loadPublicKey(ResourceLoader loader, String location)
      throws IOException {
    String pem = readResource(loader, location);
    String body = stripPem(pem);
    byte[] der = Decoders.BASE64.decode(body);
    try {
      KeyFactory kf = KeyFactory.getInstance("RSA");
      return kf.generatePublic(new X509EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalStateException("public key 로드 실패: " + location, e);
    }
  }

  private static String readResource(ResourceLoader loader, String location) throws IOException {
    Resource resource = loader.getResource(location);
    try (var is = resource.getInputStream()) {
      return new String(is.readAllBytes());
    }
  }

  private static String stripPem(String pem) {
    return pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
        .replaceAll("-----END [A-Z ]+-----", "")
        .replaceAll("\\s+", "");
  }

  /** issued access token + ttl 정보. */
  public record IssuedToken(String token, long expiresInSeconds) {}

  /** kid header → PublicKey 매핑. 미스 시 active key fallback. */
  private static final class KidKeyLocator implements Locator<Key> {
    private final Map<String, PublicKey> keys;

    KidKeyLocator(Map<String, PublicKey> keys) {
      this.keys = keys;
    }

    @Override
    public Key locate(io.jsonwebtoken.Header header) {
      if (header instanceof JwsHeader jws) {
        String kid = jws.getKeyId();
        if (kid != null) {
          PublicKey k = keys.get(kid);
          if (k != null) {
            return k;
          }
        }
      }
      // fallback: 첫 번째 등록 키
      return keys.values().stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("등록된 verification key가 없음"));
    }
  }
}

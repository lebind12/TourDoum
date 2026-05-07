package com.ssafy.tourdoum.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 — `tourdoum.jwt.*` (application.yml).
 *
 * <p>BE-1: access token 발급/검증.<br>
 * BE-2(#63): refresh ttl + previous-kid 검증 활성.
 *
 * @param issuer 토큰 {@code iss} 클레임.
 * @param accessTtl access token 유효기간 — ADR-0011 기본 15분.
 * @param refreshTtl refresh token 유효기간 — ADR-0011 기본 14일 (#63 BE-2).
 * @param activeKid 현재 활성 kid. 발급은 항상 active.
 * @param previousKid 직전 활성 kid — 검증 시 fallback. null/빈 문자열이면 미사용.
 * @param privateKeyLocation active private key classpath PEM.
 * @param publicKeyLocation active public key classpath PEM.
 * @param previousPublicKeyLocation previousKid 사용 시 그 public key classpath PEM.
 */
@ConfigurationProperties(prefix = "tourdoum.jwt")
public record JwtProperties(
    String issuer,
    Duration accessTtl,
    Duration refreshTtl,
    String activeKid,
    String previousKid,
    String privateKeyLocation,
    String publicKeyLocation,
    String previousPublicKeyLocation) {

  public JwtProperties {
    if (issuer == null || issuer.isBlank()) {
      issuer = "tourdoum";
    }
    if (accessTtl == null) {
      accessTtl = Duration.ofMinutes(15);
    }
    if (refreshTtl == null) {
      refreshTtl = Duration.ofDays(14);
    }
    if (activeKid == null || activeKid.isBlank()) {
      activeKid = "dev-1";
    }
    if (privateKeyLocation == null || privateKeyLocation.isBlank()) {
      privateKeyLocation = "classpath:keys/jwt-dev-private.pem";
    }
    if (publicKeyLocation == null || publicKeyLocation.isBlank()) {
      publicKeyLocation = "classpath:keys/jwt-dev-public.pem";
    }
  }
}

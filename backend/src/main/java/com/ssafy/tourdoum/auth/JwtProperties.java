package com.ssafy.tourdoum.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 — `tourdoum.jwt.*` (application.yml).
 *
 * <p>본 task(BE-1)는 access token 발급/검증만 다룬다. refresh/rotation/family/denylist는 BE-2 범위.
 *
 * @param issuer 토큰 {@code iss} 클레임. 발급 서버 식별자.
 * @param accessTtl access token 유효기간 — ADR-0011 기본 15분.
 * @param activeKid 현재 활성 key id ({@code kid} header). 검증 시 우선 시도.
 * @param previousKid 직전 활성 key id (rotation 직후 검증 호환). null 또는 빈 문자열이면 미사용.
 * @param privateKeyLocation active key의 PKCS#8 PEM private key classpath 위치.
 * @param publicKeyLocation active key의 PEM public key classpath 위치.
 * @param previousPublicKeyLocation previousKid가 설정된 경우 그 public key의 classpath 위치.
 */
@ConfigurationProperties(prefix = "tourdoum.jwt")
public record JwtProperties(
    String issuer,
    Duration accessTtl,
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

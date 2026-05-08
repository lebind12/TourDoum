package com.ssafy.tourdoum.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * `@SpringBootTest` 슬라이스에서 Redis 빈을 InMemory로 swap — Redis 미가동 환경에서도 auth 통합 흐름을 검증한다 (#63).
 *
 * <p>실제 IT(Testcontainers Redis)에서는 본 config을 import하지 않으면 자동으로 Redis 구현이 사용된다. ADR-0011 BE-4
 * 추가: lockout / password-reset / user revocation epoch 모두 InMemory swap.
 */
@TestConfiguration
public class InMemoryAuthTestConfig {

  @Bean
  @Primary
  public RefreshTokenStore inMemoryRefreshTokenStore() {
    return new InMemoryRefreshTokenStore();
  }

  @Bean
  @Primary
  public AccessTokenDenylist inMemoryAccessTokenDenylist() {
    return new InMemoryAccessTokenDenylist();
  }

  @Bean
  @Primary
  public LoginLockoutService inMemoryLoginLockoutService() {
    return new InMemoryLoginLockoutService();
  }

  @Bean
  @Primary
  public PasswordResetTokenStore inMemoryPasswordResetTokenStore() {
    return new InMemoryPasswordResetTokenStore();
  }

  @Bean
  @Primary
  public UserRevocationStore inMemoryUserRevocationStore() {
    return new InMemoryUserRevocationStore();
  }
}

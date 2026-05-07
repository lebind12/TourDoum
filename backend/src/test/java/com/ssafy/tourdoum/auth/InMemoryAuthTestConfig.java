package com.ssafy.tourdoum.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * `@SpringBootTest` 슬라이스에서 Redis 빈을 InMemory로 swap — Redis 미가동 환경에서도 auth 통합 흐름을 검증한다 (#63).
 *
 * <p>실제 IT(Testcontainers Redis)에서는 본 config을 import하지 않으면 자동으로 Redis 구현이 사용된다.
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
}

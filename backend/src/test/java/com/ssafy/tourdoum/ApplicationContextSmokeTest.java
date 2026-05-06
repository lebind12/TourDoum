package com.ssafy.tourdoum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 전체 컨텍스트 스모크 테스트 (ADR-0005).
 *
 * <p>목적: Docker 없이 H2 인메모리 DB로 Spring ApplicationContext가 정상 부팅되는지 검증한다.
 * 슬라이스 테스트(@WebMvcTest, @DataJpaTest)는 클래스패스/빈 와이어링 오류를 잡지 못한다.
 *
 * <p>Redis 우회 방법: {@code spring.session.store-type=none} (test/application.yml) + {@link
 * com.ssafy.tourdoum.global.RedisSessionConfig}에 추가된 {@code @ConditionalOnProperty}. store-type이
 * none이면 RedisSessionConfig 자체가 로드되지 않으므로 @EnableRedisIndexedHttpSession 이 Redis 연결을 시도하지
 * 않는다. RedisAutoConfiguration(LettuceConnectionFactory)은 여전히 로드되지만, Lettuce는 실제 명령 실행 전까지
 * TCP 연결을 맺지 않으므로 부팅 단계에서 문제가 발생하지 않는다.
 *
 * <p>실행: {@code ./mvnw test} (Surefire — Docker 불필요)
 */
@SpringBootTest
class ApplicationContextSmokeTest {

  @Test
  @DisplayName("전체 Spring ApplicationContext가 Docker 없이 정상 부팅된다")
  void contextLoads() {
    // 컨텍스트가 로드되면 성공. 별도 assertion 불필요.
  }
}

// 학습 친화 모드: 신규 테스트는 사용자가 작성. 본 파일은 패턴 참고용.
package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통합 테스트 — Testcontainers(MySQL 8.4 + Redis 7.4).
 *
 * <p>기본적으로 maven-failsafe-plugin이 실행. {@code ./mvnw verify -DskipITs=false} 또는 {@code ./mvnw verify
 * -Pit} 로 활성화.
 *
 * <p>Docker가 실행 중이어야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HealthIntegrationTest {

  @SuppressWarnings("resource")
  @Container
  static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("tourdoum")
          .withUsername("tourdoum")
          .withPassword("tourdoum");

  @SuppressWarnings("resource")
  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:mysql://"
                + mysql.getHost()
                + ":"
                + mysql.getMappedPort(3306)
                + "/tourdoum?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    // test application.yml의 org.h2.Driver 오버라이드 — MySQL Testcontainer 사용 시 필요
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    // Testcontainers 환경에서는 ddl-auto=update 로 자동 스키마 생성
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
  }

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @DisplayName("GET /api/health 는 컨테이너 기동 후 200 OK 를 반환한다")
  void health_returnsOk_withContainers() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/api/health", String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).contains("UP");
  }
}

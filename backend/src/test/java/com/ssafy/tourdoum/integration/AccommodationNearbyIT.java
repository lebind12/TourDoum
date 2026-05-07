package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.tourdoum.accommodation.AccommodationRepository;
import com.ssafy.tourdoum.accommodation.AccommodationWithDistance;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link AccommodationRepository#findNearby} 통합 테스트 — Testcontainers(MySQL 8.4 + Redis 7.4-alpine).
 *
 * <p>H2는 ST_Distance_Sphere / GENERATED STORED POINT를 지원하지 않으므로 MySQL Testcontainers로 검증한다.
 *
 * <p>AttractionNearbyIntegrationTest 동일 패턴. IT_ 접두사로 V7 시드 데이터와 격리.
 *
 * <p>실행: {@code ./mvnw verify -DskipITs=false -Dtourdoum.it=true}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@EnabledIfSystemProperty(named = "tourdoum.it", matches = "true")
class AccommodationNearbyIT {

  /** 기준 좌표: 서울시청 (37.5665, 126.9780) */
  private static final double CENTER_LAT = 37.5665;

  private static final double CENTER_LNG = 126.9780;

  private static final int LARGE_LIMIT = 1000;

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
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    // Flyway가 V1~V7 마이그레이션을 순서대로 적용 → accommodations 테이블 + SPATIAL INDEX + 시드 데이터 생성
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
  }

  @Autowired private AccommodationRepository accommodationRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  /**
   * 테스트용 숙박 4건 삽입.
   *
   * <p>IT_ 접두사로 V7 시드 데이터와 분리. INSERT IGNORE 멱등 보장.
   *
   * <p>예상 거리(서울시청 기준):
   *
   * <ul>
   *   <li>IT_을지로 호텔: ~434m
   *   <li>IT_명동 모텔: ~776m
   *   <li>IT_이태원 펜션: ~3.1km
   *   <li>IT_인천 게스트하우스: ~38km (5km 반경 밖)
   * </ul>
   */
  @BeforeEach
  void insertTestAccommodations() {
    jdbcTemplate.execute(
        """
        INSERT IGNORE INTO accommodations
          (name, type, address, lat, lng, price_from, rating, thumbnail_url, description, created_at, updated_at)
        VALUES
          ('IT_을지로 호텔',      'HOTEL',      '서울특별시 중구 을지로 33',          37.5661, 126.9819, 120000, 4.2, NULL,                                       'IT 테스트용', NOW(6), NOW(6)),
          ('IT_명동 모텔',        'MOTEL',       '서울특별시 중구 명동길 55',          37.5597, 126.9770, 55000,  3.8, 'https://example.com/it-motel.jpg',         'IT 테스트용', NOW(6), NOW(6)),
          ('IT_이태원 펜션',      'PENSION',     '서울특별시 용산구 이태원로 120',     37.5347, 126.9940, 80000,  4.1, NULL,                                       'IT 테스트용', NOW(6), NOW(6)),
          ('IT_인천 게스트하우스', 'GUESTHOUSE', '인천광역시 중구 공항로 272',         37.4602, 126.4407, 40000,  3.9, NULL,                                       'IT 테스트용', NOW(6), NOW(6))
        """);
  }

  @Test
  @DisplayName("반경 5000m 검색 — 5km 밖 인천(IT_) 제외, 나머지 IT_ 3건 포함")
  void findNearby_radius_filter() {
    List<AccommodationWithDistance> result =
        accommodationRepository.findNearby(CENTER_LAT, CENTER_LNG, 5000, LARGE_LIMIT);

    List<String> itNames =
        result.stream()
            .map(AccommodationWithDistance::getName)
            .filter(n -> n.startsWith("IT_"))
            .toList();

    assertThat(itNames).contains("IT_을지로 호텔", "IT_명동 모텔", "IT_이태원 펜션");
    assertThat(itNames).doesNotContain("IT_인천 게스트하우스");
  }

  @Test
  @DisplayName("거리 오름차순 정렬 — IT_ 기준: 을지로 호텔 < 명동 모텔 < 이태원 펜션")
  void findNearby_distance_ordering() {
    List<AccommodationWithDistance> result =
        accommodationRepository.findNearby(CENTER_LAT, CENTER_LNG, 5000, LARGE_LIMIT);

    List<AccommodationWithDistance> testRows =
        result.stream().filter(r -> r.getName().startsWith("IT_")).toList();

    assertThat(testRows).hasSizeGreaterThanOrEqualTo(3);

    List<String> orderedNames = testRows.stream().map(AccommodationWithDistance::getName).toList();
    int euljiro = orderedNames.indexOf("IT_을지로 호텔");
    int myeongdong = orderedNames.indexOf("IT_명동 모텔");
    int itaewon = orderedNames.indexOf("IT_이태원 펜션");

    assertThat(euljiro).isGreaterThanOrEqualTo(0);
    assertThat(euljiro).isLessThan(myeongdong);
    assertThat(myeongdong).isLessThan(itaewon);
  }

  @Test
  @DisplayName("thumbnailUrl 별칭 매핑 — thumbnail_url AS thumbnailUrl projection 정상 수신")
  void findNearby_thumbnailUrl_alias_mapping() {
    List<AccommodationWithDistance> result =
        accommodationRepository.findNearby(CENTER_LAT, CENTER_LNG, 5000, LARGE_LIMIT);

    AccommodationWithDistance motel =
        result.stream()
            .filter(r -> "IT_명동 모텔".equals(r.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("IT_명동 모텔 not found"));

    AccommodationWithDistance hotel =
        result.stream()
            .filter(r -> "IT_을지로 호텔".equals(r.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("IT_을지로 호텔 not found"));

    assertThat(motel.getThumbnailUrl()).isEqualTo("https://example.com/it-motel.jpg");
    assertThat(hotel.getThumbnailUrl()).isNull();
  }
}

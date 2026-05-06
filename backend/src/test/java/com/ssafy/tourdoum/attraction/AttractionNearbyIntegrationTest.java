package com.ssafy.tourdoum.attraction;

import static org.assertj.core.api.Assertions.assertThat;

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
 * {@link AttractionRepository#findNearby} 통합 테스트 — Testcontainers(MySQL 8.4 + Redis 7.4-alpine).
 *
 * <p>H2는 ST_Distance_Sphere / GENERATED STORED POINT를 지원하지 않으므로 MySQL Testcontainers로 검증한다.
 *
 * <p>V3/V4 시드 데이터와의 격리: tour_api_id="IT_" 접두사 + name="IT_" 접두사로 구분. limit 파라미터 테스트를 제외한 모든 테스트는 결과를
 * IT_ 접두사로 필터링해 V3/V4 시드 데이터 간섭을 차단한다.
 *
 * <p>실행: {@code ./mvnw verify -DskipITs=false -Dtourdoum.it=true}
 *
 * <p>Docker가 실행 중이어야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@EnabledIfSystemProperty(named = "tourdoum.it", matches = "true")
class AttractionNearbyIntegrationTest {

  /** 기준 좌표: 서울시청 (37.5665, 126.9780) */
  private static final double CENTER_LAT = 37.5665;

  private static final double CENTER_LNG = 126.9780;

  /**
   * V4 데이터는 서울시청 기준 5km 내에 다수 존재한다. 이 limit은 V3+V4+IT_ 모든 데이터를 포함하도록 충분히 크게 설정한다. (V4 총 13,155건 중
   * 5km 내 비율 감안해 5000으로 설정)
   */
  private static final int LARGE_LIMIT = 5000;

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
    // H2 드라이버 오버라이드 — test application.yml의 org.h2.Driver 대신 MySQL 드라이버 사용
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    // Flyway가 V1~V5 마이그레이션을 순서대로 적용 → location GENERATED STORED 컬럼 및 SPATIAL INDEX 생성
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    // MySQL 방언으로 교체 (test yml의 H2Dialect 오버라이드)
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
  }

  @Autowired private AttractionRepository attractionRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  /**
   * 테스트용 여행지 5건 삽입.
   *
   * <p>tour_api_id / name 모두 "IT_" 접두사로 V3/V4 시드와 분리한다. INSERT IGNORE로 재실행(멱등) 보장.
   *
   * <p>예상 거리(서울시청 기준):
   *
   * <ul>
   *   <li>IT_남대문시장: ~776m
   *   <li>IT_경복궁: ~1458m
   *   <li>IT_남산서울타워: ~1929m
   *   <li>IT_홍대거리: ~4906m (5km 반경 내)
   *   <li>IT_인천국제공항: ~44km (5km 반경 밖)
   * </ul>
   */
  @BeforeEach
  void insertTestAttractions() {
    jdbcTemplate.execute(
        """
        INSERT IGNORE INTO attractions
          (name, region, category, address, latitude, longitude,\
         description, image_url, tour_api_id, created_at, updated_at)
        VALUES
          ('IT_남대문시장',   '서울', 'SHOPPING', '서울특별시 중구 남대문시장4길 21',\
             37.5597, 126.9770, 'test', 'http://example.com/namdaemun.jpg', 'IT_namdaemun',  NOW(6), NOW(6)),
          ('IT_경복궁',       '서울', 'HISTORY',  '서울특별시 종로구 사직로 161',\
             37.5796, 126.9770, 'test', NULL,                                'IT_gyeongbok',  NOW(6), NOW(6)),
          ('IT_남산서울타워', '서울', 'NATURE',   '서울특별시 용산구 남산공원길 105',\
             37.5512, 126.9882, 'test', 'http://example.com/namsan.jpg',    'IT_namsan',     NOW(6), NOW(6)),
          ('IT_홍대거리',     '서울', 'ACTIVITY', '서울특별시 마포구 어울마당로',\
             37.5563, 126.9239, 'test', 'http://example.com/hongdae.jpg',   'IT_hongdae',    NOW(6), NOW(6)),
          ('IT_인천국제공항', '인천', 'OTHER',    '인천광역시 중구 공항로 272',\
             37.4602, 126.4407, 'test', NULL,                                'IT_incheon',    NOW(6), NOW(6))
        """);
  }

  @Test
  @DisplayName("반경 5000m 검색 — 5km 밖 인천공항(IT_)은 제외, 나머지 IT_ 4건 포함")
  void findNearby_radius_filter() {
    List<AttractionWithDistance> result =
        attractionRepository.findNearby(CENTER_LAT, CENTER_LNG, 5000, LARGE_LIMIT);

    // IT_ 접두사 행만 필터링 — V3/V4 시드 간섭 차단
    List<String> itNames =
        result.stream()
            .map(AttractionWithDistance::getName)
            .filter(n -> n.startsWith("IT_"))
            .toList();

    assertThat(itNames).contains("IT_남대문시장", "IT_경복궁", "IT_남산서울타워", "IT_홍대거리");
    assertThat(itNames).doesNotContain("IT_인천국제공항");
  }

  @Test
  @DisplayName("거리 오름차순 정렬 — IT_ 행 기준: 남대문시장 < 경복궁 < 남산서울타워 < 홍대거리")
  void findNearby_distance_ordering() {
    List<AttractionWithDistance> result =
        attractionRepository.findNearby(CENTER_LAT, CENTER_LNG, 5000, LARGE_LIMIT);

    // IT_ 접두사 행만 추출 — 전체 결과에서 이미 거리 오름차순 정렬되어 있음
    List<AttractionWithDistance> testRows =
        result.stream().filter(r -> r.getName().startsWith("IT_")).toList();

    assertThat(testRows).hasSizeGreaterThanOrEqualTo(4);

    // 전체 결과가 거리 오름차순이므로 IT_ 행도 그 순서를 유지해야 함
    List<String> orderedNames = testRows.stream().map(AttractionWithDistance::getName).toList();
    int namdaemun = orderedNames.indexOf("IT_남대문시장");
    int gyeongbok = orderedNames.indexOf("IT_경복궁");
    int namsan = orderedNames.indexOf("IT_남산서울타워");
    int hongdae = orderedNames.indexOf("IT_홍대거리");

    assertThat(namdaemun).isGreaterThanOrEqualTo(0);
    assertThat(namdaemun).isLessThan(gyeongbok);
    assertThat(gyeongbok).isLessThan(namsan);
    assertThat(namsan).isLessThan(hongdae);
  }

  @Test
  @DisplayName("거리 정확도 ±10% — IT_남대문시장 약 776m, IT_홍대거리 약 4906m")
  void findNearby_distance_accuracy() {
    List<AttractionWithDistance> result =
        attractionRepository.findNearby(CENTER_LAT, CENTER_LNG, 5000, LARGE_LIMIT);

    AttractionWithDistance namdaemun =
        result.stream()
            .filter(r -> "IT_남대문시장".equals(r.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("IT_남대문시장 not found"));

    AttractionWithDistance hongdae =
        result.stream()
            .filter(r -> "IT_홍대거리".equals(r.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("IT_홍대거리 not found"));

    // ±10% 허용 (구면 거리 계산 오차)
    assertThat(namdaemun.getDistance()).isBetween(776.0 * 0.9, 776.0 * 1.1);
    assertThat(hongdae.getDistance()).isBetween(4906.0 * 0.9, 4906.0 * 1.1);
  }

  @Test
  @DisplayName("imageUrl 별칭 매핑 — image_url AS imageUrl projection 정상 수신")
  void findNearby_imageUrl_alias_mapping() {
    List<AttractionWithDistance> result =
        attractionRepository.findNearby(CENTER_LAT, CENTER_LNG, 5000, LARGE_LIMIT);

    AttractionWithDistance namdaemun =
        result.stream()
            .filter(r -> "IT_남대문시장".equals(r.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("IT_남대문시장 not found"));

    AttractionWithDistance gyeongbok =
        result.stream()
            .filter(r -> "IT_경복궁".equals(r.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("IT_경복궁 not found"));

    // image_url이 있는 경우 projection getImageUrl()로 정상 수신
    assertThat(namdaemun.getImageUrl()).isEqualTo("http://example.com/namdaemun.jpg");
    // image_url이 NULL인 경우 null 반환
    assertThat(gyeongbok.getImageUrl()).isNull();
  }

  @Test
  @DisplayName("limit 파라미터 — 결과 건수를 limit으로 제한")
  void findNearby_limit_parameter() {
    // 반경 5000m 내에 V3+V4+IT_ 합쳐 2건 초과가 보장되므로 limit=2이면 정확히 2건
    List<AttractionWithDistance> result =
        attractionRepository.findNearby(CENTER_LAT, CENTER_LNG, 5000, 2);

    assertThat(result).hasSize(2);
  }
}

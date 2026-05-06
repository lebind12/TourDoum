package com.ssafy.tourdoum.integration.tourapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.tourdoum.integration.tourapi.TourApiAttractionMapper.SqlInsertRow;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TourAPI → V4 SQL 생성기 (독립 main 클래스).
 *
 * <p>Spring Boot 컨텍스트를 기동하지 않는다 — DB 연결 / Flyway 불필요.
 * 결과 INSERT 문은 stdout으로 출력. 스크립트가 파일로 리다이렉트한다.
 *
 * <p>ADR-0005: TOUR_API_KEY는 환경변수로만 주입. 출력 SQL에 키 미포함.
 *
 * <p>사용 예:
 * <pre>
 *   TOUR_API_KEY=... scripts/etl-tour-api.sh
 *   # 또는
 *   TOUR_API_KEY=... ./mvnw exec:java \
 *     -Dexec.mainClass="com.ssafy.tourdoum.integration.tourapi.TourApiSqlGeneratorMain" \
 *     > backend/src/main/resources/db/migration/V4__tour_api_attractions.sql
 * </pre>
 */
public class TourApiSqlGeneratorMain {

  private static final Logger log = LoggerFactory.getLogger(TourApiSqlGeneratorMain.class);

  private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";
  private static final String MOBILE_APP = "tourdoum";
  private static final int PAGE_SIZE = 100;

  private static final List<Integer> AREA_CODES =
      Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 31, 32, 33, 34, 35, 36, 37, 38, 39);
  private static final List<Integer> CONTENT_TYPE_IDS = Arrays.asList(12, 14, 28);

  public static void main(String[] args) throws Exception {
    String serviceKey = System.getenv("TOUR_API_KEY");
    if (serviceKey == null || serviceKey.isBlank()) {
      System.err.println("✗ TOUR_API_KEY 환경변수가 설정되지 않았습니다.");
      System.err.println("  .env 파일 또는 환경변수에 TOUR_API_KEY를 주입한 후 재실행하세요.");
      System.err.println("  예) export TOUR_API_KEY=<발급받은 인코딩 키>");
      System.exit(1);
    }

    TourApiProperties props =
        new TourApiProperties(
            BASE_URL, serviceKey, MOBILE_APP, PAGE_SIZE, AREA_CODES, CONTENT_TYPE_IDS);

    ObjectMapper objectMapper = new ObjectMapper();
    TourApiStandaloneClient standaloneClient = new TourApiStandaloneClient(props, objectMapper);
    TourApiAttractionMapper mapper = new TourApiAttractionMapper();

    PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

    String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    out.println("-- V4__tour_api_attractions.sql");
    out.println("-- 생성일시: " + now);
    out.println("-- 출처: 한국관광공사 TourAPI 4.0 (KorService2)");
    out.println("-- contentTypeId: 12(관광지)→OTHER, 14(문화시설)→HISTORY, 28(레포츠)→ACTIVITY");
    out.println("-- ADR-0005: docs/adr/0005-tour-api-integration.md");
    out.println("-- 주의: 자동 생성 파일. 직접 편집 금지. 재생성: scripts/etl-tour-api.sh");
    out.println();

    Set<String> seenIds = new LinkedHashSet<>();
    int totalFetched = 0;
    int totalInserted = 0;
    int totalSkipped = 0;
    int totalDuplicate = 0;

    for (int contentTypeId : CONTENT_TYPE_IDS) {
      for (int areaCode : AREA_CODES) {
        log.info("수집 중: areaCode={} contentTypeId={}", areaCode, contentTypeId);
        List<TourApiAreaItem> items;
        try {
          items = standaloneClient.fetchAll(areaCode, contentTypeId);
        } catch (Exception e) {
          log.warn(
              "수집 실패 areaCode={} contentTypeId={}: {}", areaCode, contentTypeId, e.getMessage());
          continue;
        }

        totalFetched += items.size();

        for (TourApiAreaItem item : items) {
          SqlInsertRow row = mapper.map(item);
          if (row == null) {
            totalSkipped++;
            continue;
          }
          if (row.tourApiId() != null && seenIds.contains(row.tourApiId())) {
            totalDuplicate++;
            continue;
          }
          if (row.tourApiId() != null) {
            seenIds.add(row.tourApiId());
          }

          out.println(toInsertSql(row));
          totalInserted++;
        }
      }
    }

    out.println();
    out.printf(
        "-- 통계: API 수신=%d / INSERT=%d / 좌표·타이틀 스킵=%d / 중복 스킵=%d%n",
        totalFetched, totalInserted, totalSkipped, totalDuplicate);

    System.err.printf(
        "ETL 완료: API 수신=%d / INSERT=%d / 스킵=%d / 중복=%d%n",
        totalFetched, totalInserted, totalSkipped, totalDuplicate);
  }

  /**
   * SqlInsertRow → SQL INSERT 문 생성.
   *
   * <p>H2 호환을 위해 INSERT IGNORE / ON DUPLICATE KEY 미사용.
   * 중복 제거는 SQL Generator 레벨 (ADR-0005).
   */
  static String toInsertSql(SqlInsertRow row) {
    String nameSql = sqlStr(row.name());
    String regionSql = sqlStr(row.region());
    String categorySql = row.category() != null ? sqlStr(row.category()) : "'OTHER'";
    String addressSql = sqlStr(row.address());
    String imageUrlSql = sqlStr(row.imageUrl());
    String tourApiIdSql = sqlStr(row.tourApiId());

    return String.format(
        "INSERT INTO attractions"
            + " (name, region, category, address, latitude, longitude,"
            + " image_url, tour_api_id, created_at, updated_at)"
            + " VALUES (%s, %s, %s, %s, %.6f, %.6f, %s, %s, NOW(6), NOW(6));",
        nameSql,
        regionSql,
        categorySql,
        addressSql,
        row.latitude(),
        row.longitude(),
        imageUrlSql,
        tourApiIdSql);
  }

  private static String sqlStr(String value) {
    if (value == null) {
      return "NULL";
    }
    return "'" + value + "'";
  }
}

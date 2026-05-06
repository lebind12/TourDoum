package com.ssafy.tourdoum.integration.tourapi;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TourAPI DTO → SQL INSERT 행 변환기.
 *
 * <p>ADR-0005:
 * - mapx/mapy 가 비어있거나 0.0이면 스킵 (null 반환).
 * - title 이 null/blank면 스킵.
 * - areaCode → 한국어 지역명 매핑 (17개 광역 단위).
 * - contentTypeId → AttractionCategory 매핑 (12→OTHER, 14→HISTORY, 28→ACTIVITY).
 */
@Component
public class TourApiAttractionMapper {

  private static final Logger log = LoggerFactory.getLogger(TourApiAttractionMapper.class);

  /** 지역코드 → 한국어명 */
  static final Map<String, String> AREA_CODE_TO_REGION =
      Map.ofEntries(
          Map.entry("1", "서울"),
          Map.entry("2", "인천"),
          Map.entry("3", "대전"),
          Map.entry("4", "대구"),
          Map.entry("5", "광주"),
          Map.entry("6", "부산"),
          Map.entry("7", "울산"),
          Map.entry("8", "세종"),
          Map.entry("31", "경기"),
          Map.entry("32", "강원"),
          Map.entry("33", "충북"),
          Map.entry("34", "충남"),
          Map.entry("35", "경북"),
          Map.entry("36", "경남"),
          Map.entry("37", "전북"),
          Map.entry("38", "전남"),
          Map.entry("39", "제주"));

  /** contentTypeId → AttractionCategory 문자열 */
  static final Map<String, String> CONTENT_TYPE_TO_CATEGORY =
      Map.of(
          "12", "OTHER",
          "14", "HISTORY",
          "28", "ACTIVITY");

  /**
   * TourAPI 응답 아이템을 SQL INSERT VALUES 파라미터 문자열로 변환.
   *
   * @return SQL VALUES 절 문자열, 스킵 대상이면 null.
   */
  public SqlInsertRow map(TourApiAreaItem item) {
    // title 검증
    if (item.title() == null || item.title().isBlank()) {
      log.debug("스킵: title 없음 contentId={}", item.contentId());
      return null;
    }

    // 좌표 검증
    double lng = parseCoord(item.mapx());
    double lat = parseCoord(item.mapy());
    if (lng == 0.0 || lat == 0.0) {
      log.debug("스킵: 좌표 없음 contentId={} mapx={} mapy={}", item.contentId(), item.mapx(), item.mapy());
      return null;
    }

    String region = AREA_CODE_TO_REGION.getOrDefault(item.areaCode(), "기타");
    String category = CONTENT_TYPE_TO_CATEGORY.getOrDefault(item.contentTypeId(), "OTHER");

    String name = sanitize(item.title());
    String address = buildAddress(item.addr1(), item.addr2());
    String imageUrl = (item.firstImage() != null && !item.firstImage().isBlank())
        ? sanitize(item.firstImage()) : null;
    String tourApiId = sanitize(item.contentId());

    return new SqlInsertRow(name, region, category, address, lat, lng, imageUrl, tourApiId);
  }

  private double parseCoord(String value) {
    if (value == null || value.isBlank()) {
      return 0.0;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  private String buildAddress(String addr1, String addr2) {
    String a1 = (addr1 != null) ? addr1.trim() : "";
    String a2 = (addr2 != null) ? addr2.trim() : "";
    if (a1.isEmpty() && a2.isEmpty()) {
      return null;
    }
    return a2.isEmpty() ? a1 : (a1 + " " + a2).trim();
  }

  /** SQL 인젝션 방지: 단순 이스케이프 (작은따옴표 → ''). */
  private String sanitize(String value) {
    if (value == null) {
      return null;
    }
    return value.replace("'", "''");
  }

  /** SQL INSERT에 사용할 행 데이터 */
  public record SqlInsertRow(
      String name,
      String region,
      String category,
      String address,
      double latitude,
      double longitude,
      String imageUrl,
      String tourApiId) {}
}

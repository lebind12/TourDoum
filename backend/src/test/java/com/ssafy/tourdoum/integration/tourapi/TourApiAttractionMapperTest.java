package com.ssafy.tourdoum.integration.tourapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.tourdoum.integration.tourapi.TourApiAttractionMapper.SqlInsertRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TourApiAttractionMapper 단위 테스트. */
class TourApiAttractionMapperTest {

  private final TourApiAttractionMapper mapper = new TourApiAttractionMapper();

  @Test
  @DisplayName("정상 아이템 → SqlInsertRow 변환 성공")
  void map_validItem_returnsRow() {
    TourApiAreaItem item =
        new TourApiAreaItem(
            "126559",
            "12",
            "경복궁",
            "서울특별시 종로구 사직로 161",
            "",
            "126.976898",
            "37.579617",
            "https://example.com/image.jpg",
            "1");

    SqlInsertRow row = mapper.map(item);

    assertThat(row).isNotNull();
    assertThat(row.name()).isEqualTo("경복궁");
    assertThat(row.region()).isEqualTo("서울");
    assertThat(row.category()).isEqualTo("OTHER");
    assertThat(row.latitude()).isEqualTo(37.579617);
    assertThat(row.longitude()).isEqualTo(126.976898);
    assertThat(row.tourApiId()).isEqualTo("126559");
    assertThat(row.address()).isEqualTo("서울특별시 종로구 사직로 161");
    assertThat(row.imageUrl()).isEqualTo("https://example.com/image.jpg");
  }

  @Test
  @DisplayName("mapx/mapy 가 빈 문자열이면 null 반환 (스킵)")
  void map_emptyCoords_returnsNull() {
    TourApiAreaItem item =
        new TourApiAreaItem("99999", "12", "좌표없는명소", "주소", "", "", "", null, "1");

    SqlInsertRow row = mapper.map(item);

    assertThat(row).isNull();
  }

  @Test
  @DisplayName("mapx/mapy 가 0.0이면 null 반환 (스킵)")
  void map_zeroCoords_returnsNull() {
    TourApiAreaItem item =
        new TourApiAreaItem("88888", "12", "0좌표명소", "주소", "", "0", "0", null, "1");

    SqlInsertRow row = mapper.map(item);

    assertThat(row).isNull();
  }

  @Test
  @DisplayName("title 이 null이면 null 반환 (스킵)")
  void map_nullTitle_returnsNull() {
    TourApiAreaItem item =
        new TourApiAreaItem("77777", "12", null, "주소", "", "127.0", "37.0", null, "1");

    SqlInsertRow row = mapper.map(item);

    assertThat(row).isNull();
  }

  @Test
  @DisplayName("contentTypeId=14 → HISTORY 카테고리 매핑")
  void map_contentType14_mapsToHistory() {
    TourApiAreaItem item =
        new TourApiAreaItem("55555", "14", "국립박물관", "서울", "", "126.98", "37.52", null, "1");

    SqlInsertRow row = mapper.map(item);

    assertThat(row).isNotNull();
    assertThat(row.category()).isEqualTo("HISTORY");
  }

  @Test
  @DisplayName("contentTypeId=28 → ACTIVITY 카테고리 매핑")
  void map_contentType28_mapsToActivity() {
    TourApiAreaItem item =
        new TourApiAreaItem("44444", "28", "스키장", "강원", "", "128.5", "37.8", null, "32");

    SqlInsertRow row = mapper.map(item);

    assertThat(row).isNotNull();
    assertThat(row.category()).isEqualTo("ACTIVITY");
  }

  @Test
  @DisplayName("addr1 + addr2 조합 → address 통합")
  void map_addr1AndAddr2_combined() {
    TourApiAreaItem item =
        new TourApiAreaItem("33333", "12", "복합주소명소", "서울 종로구", "1층", "126.9", "37.5", null, "1");

    SqlInsertRow row = mapper.map(item);

    assertThat(row).isNotNull();
    assertThat(row.address()).isEqualTo("서울 종로구 1층");
  }

  @Test
  @DisplayName("SQL INSERT 문 생성 시 작은따옴표 이스케이프")
  void toInsertSql_apostropheEscaped() {
    TourApiAreaItem item =
        new TourApiAreaItem("22222", "12", "이 름'테스트", "주소'있음", "", "127.0", "37.0", null, "1");

    SqlInsertRow row = mapper.map(item);
    assertThat(row).isNotNull();
    // name 에 '' 이스케이프 적용 확인
    assertThat(row.name()).isEqualTo("이 름''테스트");
  }
}

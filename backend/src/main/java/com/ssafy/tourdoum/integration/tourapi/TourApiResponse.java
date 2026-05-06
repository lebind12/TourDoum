package com.ssafy.tourdoum.integration.tourapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * TourAPI JSON 응답 최상위 래퍼.
 *
 * <p>구조: response.body.items.item[].
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiResponse(@JsonProperty("response") Response response) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Response(@JsonProperty("header") Header header, @JsonProperty("body") Body body) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Header(
      @JsonProperty("resultCode") String resultCode, @JsonProperty("resultMsg") String resultMsg) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Body(
      @JsonProperty("items") Items items,
      @JsonProperty("totalCount") int totalCount,
      @JsonProperty("numOfRows") int numOfRows,
      @JsonProperty("pageNo") int pageNo) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Items(@JsonProperty("item") List<TourApiAreaItem> item) {
    /** item 이 null (결과 0건) 일 때 빈 리스트 반환. */
    public List<TourApiAreaItem> itemSafe() {
      return item == null ? List.of() : item;
    }
  }
}

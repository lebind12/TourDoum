package com.ssafy.tourdoum.integration.tourapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TourAPI areaBasedList2 응답 단건 DTO.
 *
 * <p>응답 필드 중 ETL에 필요한 것만 매핑. 나머지는 @JsonIgnoreProperties로 무시.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiAreaItem(
    @JsonProperty("contentid") String contentId,
    @JsonProperty("contenttypeid") String contentTypeId,
    @JsonProperty("title") String title,
    @JsonProperty("addr1") String addr1,
    @JsonProperty("addr2") String addr2,
    @JsonProperty("mapx") String mapx,
    @JsonProperty("mapy") String mapy,
    @JsonProperty("firstimage") String firstImage,
    @JsonProperty("areacode") String areaCode) {}

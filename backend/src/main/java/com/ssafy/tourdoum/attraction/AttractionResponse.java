package com.ssafy.tourdoum.attraction;

import java.math.BigDecimal;

/**
 * 여행지 단건 응답 DTO.
 *
 * @param distanceMeters 반경 검색 시 거리(미터). 목록/단건 조회에서는 null.
 */
public record AttractionResponse(
    Long id,
    String name,
    String region,
    AttractionCategory category,
    String address,
    BigDecimal latitude,
    BigDecimal longitude,
    String description,
    String imageUrl,
    Double distanceMeters) {

  /** Attraction 엔티티에서 변환 (거리 없음). */
  public static AttractionResponse from(Attraction attraction) {
    return new AttractionResponse(
        attraction.getId(),
        attraction.getName(),
        attraction.getRegion(),
        attraction.getCategory(),
        attraction.getAddress(),
        attraction.getLatitude(),
        attraction.getLongitude(),
        attraction.getDescription(),
        attraction.getImageUrl(),
        null);
  }

  /** Attraction 엔티티 + 거리 정보로 변환 (반경 검색용). */
  public static AttractionResponse from(Attraction attraction, Double distanceMeters) {
    return new AttractionResponse(
        attraction.getId(),
        attraction.getName(),
        attraction.getRegion(),
        attraction.getCategory(),
        attraction.getAddress(),
        attraction.getLatitude(),
        attraction.getLongitude(),
        attraction.getDescription(),
        attraction.getImageUrl(),
        distanceMeters);
  }
}

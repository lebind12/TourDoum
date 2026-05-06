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

  /**
   * AttractionWithDistance projection에서 변환 (반경 검색 + distanceMeters 포함).
   *
   * <p>ADR-0007: native query projection → DTO. projection의 {@code getCategory()}는 String이므로 enum
   * 변환을 수행한다.
   */
  public static AttractionResponse from(AttractionWithDistance projection) {
    return new AttractionResponse(
        projection.getId(),
        projection.getName(),
        projection.getRegion(),
        AttractionCategory.valueOf(projection.getCategory()),
        projection.getAddress(),
        projection.getLatitude(),
        projection.getLongitude(),
        projection.getDescription(),
        projection.getImageUrl(),
        projection.getDistance());
  }
}

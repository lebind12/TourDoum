package com.ssafy.tourdoum.accommodation;

import java.math.BigDecimal;

/**
 * 숙박 단건 응답 DTO.
 *
 * @param distanceMeters 반경 검색 시 거리(미터). 목록/단건 조회에서는 null.
 */
public record AccommodationResponse(
    Long id,
    String name,
    AccommodationType type,
    String address,
    BigDecimal lat,
    BigDecimal lng,
    Integer priceFrom,
    BigDecimal rating,
    String thumbnailUrl,
    String description,
    Double distanceMeters) {

  /** Accommodation 엔티티에서 변환 (거리 없음). */
  public static AccommodationResponse from(Accommodation accommodation) {
    return new AccommodationResponse(
        accommodation.getId(),
        accommodation.getName(),
        accommodation.getType(),
        accommodation.getAddress(),
        accommodation.getLat(),
        accommodation.getLng(),
        accommodation.getPriceFrom(),
        accommodation.getRating(),
        accommodation.getThumbnailUrl(),
        accommodation.getDescription(),
        null);
  }

  /**
   * AccommodationWithDistance projection에서 변환 (반경 검색 + distanceMeters 포함).
   *
   * <p>ADR-0007: native query projection → DTO. projection의 {@code getType()}는 String이므로 enum 변환을
   * 수행한다.
   */
  public static AccommodationResponse from(AccommodationWithDistance projection) {
    return new AccommodationResponse(
        projection.getId(),
        projection.getName(),
        AccommodationType.valueOf(projection.getType()),
        projection.getAddress(),
        projection.getLat(),
        projection.getLng(),
        projection.getPriceFrom(),
        projection.getRating(),
        projection.getThumbnailUrl(),
        projection.getDescription(),
        projection.getDistance());
  }
}

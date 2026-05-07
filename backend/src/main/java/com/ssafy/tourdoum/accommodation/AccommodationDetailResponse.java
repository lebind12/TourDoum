package com.ssafy.tourdoum.accommodation;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 숙박 상세 응답 DTO — GET /api/accommodations/{id} 전용.
 *
 * <p>list 응답({@link AccommodationResponse})과 분리. 상세 화면에서만 필요한
 * amenities/maxGuests/checkIn/checkOut/imageUrl 과 후기 집계 reviewCount 를 포함한다. payload 비용을 list-side에
 * 부담시키지 않는 게 분리 목적.
 *
 * @param amenities 편의시설 목록 (DB는 comma-separated 문자열, 응답은 List&lt;String&gt;).
 * @param reviewCount 본 숙박을 대상으로 작성된 후기 건수. {@code ReviewRepository.aggregateByTarget} 결과의 COUNT(r)
 *     사용.
 */
public record AccommodationDetailResponse(
    Long id,
    String name,
    AccommodationType type,
    String address,
    BigDecimal lat,
    BigDecimal lng,
    Integer priceFrom,
    BigDecimal rating,
    String thumbnailUrl,
    String imageUrl,
    String description,
    List<String> amenities,
    int maxGuests,
    String checkInTime,
    String checkOutTime,
    long reviewCount) {

  /** 엔티티 + reviewCount 를 받아 detail DTO로 변환. */
  public static AccommodationDetailResponse from(Accommodation accommodation, long reviewCount) {
    return new AccommodationDetailResponse(
        accommodation.getId(),
        accommodation.getName(),
        accommodation.getType(),
        accommodation.getAddress(),
        accommodation.getLat(),
        accommodation.getLng(),
        accommodation.getPriceFrom(),
        accommodation.getRating(),
        accommodation.getThumbnailUrl(),
        accommodation.getImageUrl(),
        accommodation.getDescription(),
        splitAmenities(accommodation.getAmenities()),
        accommodation.getMaxGuests(),
        accommodation.getCheckInTime(),
        accommodation.getCheckOutTime(),
        reviewCount);
  }

  /** comma-separated → List. 빈 문자열은 빈 리스트. trim. */
  private static List<String> splitAmenities(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}

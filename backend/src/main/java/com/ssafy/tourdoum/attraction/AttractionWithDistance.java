package com.ssafy.tourdoum.attraction;

import java.math.BigDecimal;

/**
 * Native query projection — Attraction 컬럼 + ST_Distance_Sphere 계산값.
 *
 * <p>ADR-0007: JPA + projection 패턴. findNearby 네이티브 쿼리가 distance 별칭을 명시적으로 노출하므로, Attraction 엔티티가
 * 아닌 이 projection으로 수신한다.
 *
 * <p>Spring Data JPA 네이티브 쿼리 projection 규칙: getter 이름에서 "get"을 제거하고 첫 글자를 소문자로 변환한 값이 쿼리 컬럼 별칭과
 * 일치해야 한다. 예: {@code getImageUrl()} → 별칭 {@code imageUrl}.
 */
public interface AttractionWithDistance {

  Long getId();

  String getName();

  String getRegion();

  /** native query는 String 반환. AttractionResponse.from()에서 enum 변환. */
  String getCategory();

  String getAddress();

  BigDecimal getLatitude();

  BigDecimal getLongitude();

  String getDescription();

  /** 쿼리 별칭 {@code imageUrl} 과 매핑. */
  String getImageUrl();

  /** ST_Distance_Sphere 계산값 (미터). */
  Double getDistance();
}

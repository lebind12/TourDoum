package com.ssafy.tourdoum.accommodation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 숙박 엔티티.
 *
 * <p>ADR-0002 패턴 적용: location POINT는 DB에서 GENERATED ALWAYS AS (ST_SRID(POINT(longitude, latitude),
 * 4326)) STORED로 관리. JPA는 latitude/longitude DECIMAL만 다루고 location 컬럼은 매핑하지 않는다.
 */
@Entity
@Table(name = "accommodations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Accommodation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccommodationType type;

  @Column(length = 500)
  private String address;

  @Column(nullable = false, precision = 9, scale = 6)
  private BigDecimal lat;

  @Column(nullable = false, precision = 9, scale = 6)
  private BigDecimal lng;

  /** 1박 최저가 (원 단위). */
  @Column(name = "price_from")
  private Integer priceFrom;

  /** 평균 별점 (0.0 ~ 5.0). */
  @Column(precision = 3, scale = 1)
  private BigDecimal rating;

  @Column(name = "thumbnail_url", length = 1000)
  private String thumbnailUrl;

  /** Hero 이미지 URL — 상세 화면용. 카드 카탈로그는 thumbnailUrl 유지. */
  @Column(name = "image_url", length = 1000)
  private String imageUrl;

  /** Comma-separated amenities. e.g., {@code "Wi-Fi,주차,조식"}. DTO에서 List&lt;String&gt;로 split. */
  @Column(nullable = false, length = 500)
  private String amenities;

  /** 최대 투숙 인원. */
  @Column(name = "max_guests", nullable = false)
  private int maxGuests;

  /** Check-in 시각 ({@code "HH:mm"}). */
  @Column(name = "check_in_time", nullable = false, length = 5)
  private String checkInTime;

  /** Check-out 시각 ({@code "HH:mm"}). */
  @Column(name = "check_out_time", nullable = false, length = 5)
  private String checkOutTime;

  @Column(columnDefinition = "TEXT")
  private String description;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public Accommodation(
      String name,
      AccommodationType type,
      String address,
      BigDecimal lat,
      BigDecimal lng,
      Integer priceFrom,
      BigDecimal rating,
      String thumbnailUrl,
      String imageUrl,
      String amenities,
      Integer maxGuests,
      String checkInTime,
      String checkOutTime,
      String description) {
    this.name = name;
    this.type = type;
    this.address = address;
    this.lat = lat;
    this.lng = lng;
    this.priceFrom = priceFrom;
    this.rating = rating;
    this.thumbnailUrl = thumbnailUrl;
    this.imageUrl = imageUrl;
    // NOT NULL 컬럼 default — builder 미지정 시 안전 기본값.
    this.amenities = amenities == null ? "" : amenities;
    this.maxGuests = maxGuests == null ? 2 : maxGuests;
    this.checkInTime = checkInTime == null ? "15:00" : checkInTime;
    this.checkOutTime = checkOutTime == null ? "11:00" : checkOutTime;
    this.description = description;
  }
}

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
      String description) {
    this.name = name;
    this.type = type;
    this.address = address;
    this.lat = lat;
    this.lng = lng;
    this.priceFrom = priceFrom;
    this.rating = rating;
    this.thumbnailUrl = thumbnailUrl;
    this.description = description;
  }
}

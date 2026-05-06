package com.ssafy.tourdoum.attraction;

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
 * 여행지 엔티티.
 *
 * <p>ADR-0002: location POINT는 DB에서 GENERATED ALWAYS AS (ST_SRID(POINT(longitude, latitude), 4326))
 * STORED로 관리. JPA는 latitude/longitude DECIMAL만 다루고 location 컬럼은 매핑하지 않는다.
 */
@Entity
@Table(name = "attractions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attraction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(nullable = false, length = 50)
  private String region;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AttractionCategory category;

  @Column(length = 500)
  private String address;

  @Column(nullable = false, precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(length = 1000)
  private String imageUrl;

  /** 한국관광공사 TourAPI 콘텐츠 ID. 시드 데이터에서는 null 허용. */
  @Column(length = 50)
  private String tourApiId;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public Attraction(
      String name,
      String region,
      AttractionCategory category,
      String address,
      BigDecimal latitude,
      BigDecimal longitude,
      String description,
      String imageUrl,
      String tourApiId) {
    this.name = name;
    this.region = region;
    this.category = category;
    this.address = address;
    this.latitude = latitude;
    this.longitude = longitude;
    this.description = description;
    this.imageUrl = imageUrl;
    this.tourApiId = tourApiId;
  }
}

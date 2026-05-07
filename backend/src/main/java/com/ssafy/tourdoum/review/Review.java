package com.ssafy.tourdoum.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 후기 엔티티. */
@Entity
@Table(name = "reviews")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 20)
  private ReviewTargetType targetType;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  /** 별점 (1-5). */
  @Column(nullable = false)
  private int rating;

  @Column(length = 200)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String content;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public Review(
      Long memberId,
      ReviewTargetType targetType,
      Long targetId,
      int rating,
      String title,
      String content) {
    this.memberId = memberId;
    this.targetType = targetType;
    this.targetId = targetId;
    this.rating = rating;
    this.title = title;
    this.content = content;
  }
}

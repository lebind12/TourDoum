package com.ssafy.tourdoum.favorite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 즐겨찾기 엔티티.
 *
 * <p>UNIQUE(member_id, target_type, target_id)로 중복 토글을 DB 레벨에서 방지한다.
 */
@Entity
@Table(
    name = "favorites",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_favorites_member_target",
          columnNames = {"member_id", "target_type", "target_id"})
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 20)
  private FavoriteTargetType targetType;

  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  public Favorite(Long memberId, FavoriteTargetType targetType, Long targetId) {
    this.memberId = memberId;
    this.targetType = targetType;
    this.targetId = targetId;
  }
}

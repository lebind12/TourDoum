package com.ssafy.tourdoum.notification;

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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 알림 엔티티. readAt null → 미읽음. */
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private NotificationType type;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 500)
  private String body;

  @Column(name = "link_url", length = 500)
  private String linkUrl;

  /** null = 미읽음, non-null = 읽음 처리 시각. */
  @Column(name = "read_at")
  private LocalDateTime readAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  public Notification(
      Long memberId, NotificationType type, String title, String body, String linkUrl) {
    this.memberId = memberId;
    this.type = type;
    this.title = title;
    this.body = body;
    this.linkUrl = linkUrl;
  }

  /** 읽음 처리. */
  public void markRead() {
    if (this.readAt == null) {
      this.readAt = LocalDateTime.now();
    }
  }

  /** 미읽음 여부. */
  public boolean isUnread() {
    return this.readAt == null;
  }
}

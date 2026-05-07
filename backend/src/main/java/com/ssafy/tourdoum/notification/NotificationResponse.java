package com.ssafy.tourdoum.notification;

import java.time.LocalDateTime;

/** 알림 응답 DTO. */
public record NotificationResponse(
    Long id,
    Long memberId,
    NotificationType type,
    String title,
    String body,
    String linkUrl,
    boolean unread,
    LocalDateTime createdAt,
    LocalDateTime readAt) {

  /** Notification 엔티티 → 응답 변환. */
  public static NotificationResponse from(Notification n) {
    return new NotificationResponse(
        n.getId(),
        n.getMemberId(),
        n.getType(),
        n.getTitle(),
        n.getBody(),
        n.getLinkUrl(),
        n.isUnread(),
        n.getCreatedAt(),
        n.getReadAt());
  }
}

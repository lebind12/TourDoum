package com.ssafy.tourdoum.notification;

/** 알림 미존재 예외 (→ 404). */
public class NotificationNotFoundException extends RuntimeException {

  public NotificationNotFoundException(Long id) {
    super("알림을 찾을 수 없습니다. id=" + id);
  }
}

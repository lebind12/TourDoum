package com.ssafy.tourdoum.notification;

/** 알림 읽음 처리 권한 없음 예외 (→ 403). */
public class NotificationForbiddenException extends RuntimeException {

  public NotificationForbiddenException(Long id) {
    super("알림에 대한 권한이 없습니다. id=" + id);
  }
}

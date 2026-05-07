package com.ssafy.tourdoum.chat;

/** 채팅 채널 접근 권한 없음 예외 (→ 403). */
public class ChatForbiddenException extends RuntimeException {

  public ChatForbiddenException(Long channelId) {
    super("채팅 채널에 대한 권한이 없습니다. channelId=" + channelId);
  }
}

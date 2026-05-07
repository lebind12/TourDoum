package com.ssafy.tourdoum.chat;

/** 채팅 채널 미존재 예외 (→ 404). */
public class ChatChannelNotFoundException extends RuntimeException {

  public ChatChannelNotFoundException(Long channelId) {
    super("채팅 채널을 찾을 수 없습니다. channelId=" + channelId);
  }
}

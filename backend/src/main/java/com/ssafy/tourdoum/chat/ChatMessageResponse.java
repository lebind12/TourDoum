package com.ssafy.tourdoum.chat;

import java.time.LocalDateTime;

/** 채팅 메시지 응답 DTO. */
public record ChatMessageResponse(
    Long id, Long channelId, Long senderId, String content, LocalDateTime createdAt) {

  /** ChatMessage 엔티티 → 응답 변환. */
  public static ChatMessageResponse from(ChatMessage message) {
    return new ChatMessageResponse(
        message.getId(),
        message.getChannelId(),
        message.getSenderId(),
        message.getContent(),
        message.getCreatedAt());
  }
}

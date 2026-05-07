package com.ssafy.tourdoum.chat;

import java.time.LocalDateTime;

/** 채팅 채널 응답 DTO. */
public record ChatChannelResponse(
    Long id, String name, ChatChannelType type, LocalDateTime createdAt) {

  /** ChatChannel 엔티티 → 응답 변환. */
  public static ChatChannelResponse from(ChatChannel channel) {
    return new ChatChannelResponse(
        channel.getId(), channel.getName(), channel.getType(), channel.getCreatedAt());
  }
}

package com.ssafy.tourdoum.chat;

import java.time.LocalDateTime;

/**
 * 채팅 채널 응답 DTO.
 *
 * <p>ADR-0012 v2 BE-2: lastMessageId/At 노출 — FE 채널 목록 정렬/미리보기.
 */
public record ChatChannelResponse(
    Long id,
    String name,
    ChatChannelType type,
    LocalDateTime createdAt,
    Long lastMessageId,
    LocalDateTime lastMessageAt) {

  /** ChatChannel 엔티티 → 응답 변환. */
  public static ChatChannelResponse from(ChatChannel channel) {
    return new ChatChannelResponse(
        channel.getId(),
        channel.getName(),
        channel.getType(),
        channel.getCreatedAt(),
        channel.getLastMessageId(),
        channel.getLastMessageAt());
  }
}

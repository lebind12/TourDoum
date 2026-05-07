package com.ssafy.tourdoum.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 채팅 채널 구성원 엔티티. 복합 PK (channelId, memberId). */
@Entity
@Table(name = "chat_members")
@IdClass(ChatMemberId.class)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMember {

  @Id
  @Column(name = "channel_id", nullable = false)
  private Long channelId;

  @Id
  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @CreatedDate
  @Column(name = "joined_at", nullable = false, updatable = false)
  private LocalDateTime joinedAt;

  /** 마지막 읽은 메시지 ID (null = 읽은 메시지 없음). */
  @Column(name = "last_read_message_id")
  private Long lastReadMessageId;

  @Builder
  public ChatMember(Long channelId, Long memberId) {
    this.channelId = channelId;
    this.memberId = memberId;
  }

  /** 마지막 읽은 메시지 ID 갱신. */
  public void updateLastRead(Long messageId) {
    this.lastReadMessageId = messageId;
  }
}

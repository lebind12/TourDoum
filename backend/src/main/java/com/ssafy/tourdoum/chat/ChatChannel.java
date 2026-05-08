package com.ssafy.tourdoum.chat;

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

/**
 * 채팅 채널 엔티티. PUBLIC = 공개, DM = 1:1 다이렉트 메시지.
 *
 * <p>ADR-0012 v2 BE-2 추가 컬럼:
 *
 * <ul>
 *   <li>{@code dmMemberMin/Max} — DM 채널의 두 멤버 ID(min < max). PUBLIC은 NULL. UNIQUE INDEX + CHECK가
 *       V17에서 부여돼 같은 pair 중복 채널을 DB level에서 차단.
 *   <li>{@code lastMessageId/At} — 채팅방 목록 N+1 회피 + 정렬용 denormalize. {@code
 *       ChatChannelRepository#updateLastMessage} guarded UPDATE로만 갱신(monotonic).
 * </ul>
 */
@Entity
@Table(name = "chat_channels")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatChannel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ChatChannelType type;

  /** DM pair min — V17. PUBLIC은 NULL. */
  @Column(name = "dm_member_min")
  private Long dmMemberMin;

  /** DM pair max — V17. PUBLIC은 NULL. */
  @Column(name = "dm_member_max")
  private Long dmMemberMax;

  /** 마지막 메시지 ID — denormalize, V17. */
  @Column(name = "last_message_id")
  private Long lastMessageId;

  /** 마지막 메시지 시각 — denormalize, V17. 정렬용 인덱스 적용. */
  @Column(name = "last_message_at")
  private LocalDateTime lastMessageAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  public ChatChannel(String name, ChatChannelType type, Long dmMemberMin, Long dmMemberMax) {
    this.name = name;
    this.type = type;
    this.dmMemberMin = dmMemberMin;
    this.dmMemberMax = dmMemberMax;
  }
}

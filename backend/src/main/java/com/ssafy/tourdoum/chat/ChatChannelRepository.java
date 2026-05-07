package com.ssafy.tourdoum.chat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 채팅 채널 JPA 레포지토리.
 *
 * <p>ADR-0012 v2 BE-2:
 *
 * <ul>
 *   <li>{@link #findMyChannels} — IN 서브쿼리 → JOIN. 역방향 인덱스 {@code idx_chat_members_member} 활용. 정렬 키는
 *       last_message_at DESC + id DESC (NULL은 createdAt fallback).
 *   <li>{@link #findDmByMemberPair} — UNIQUE 인덱스 단일 lookup. PUBLIC은 두 컬럼 NULL이라 매칭 안 됨.
 *   <li>{@link #updateLastMessage} — monotonic guarded UPDATE. 늦게 도착한 INSERT는 무시되어 stale로 후퇴하지 않는다.
 * </ul>
 */
public interface ChatChannelRepository extends JpaRepository<ChatChannel, Long> {

  /**
   * 회원이 속한 채널 목록 — JOIN 패턴, 정렬은 last_message_at DESC NULLS LAST 흉내(COALESCE).
   *
   * <p>chat_members PK가 (channel_id, member_id)라 member_id 단독 lookup은 leftmost 위반 → V17
   * idx_chat_members_member 역방향 인덱스로 처방. 본 쿼리는 그 인덱스 prefix로 chat_members → JOIN chat_channels.
   */
  @Query(
      """
      SELECT c FROM ChatChannel c
      JOIN ChatMember cm ON cm.channelId = c.id
      WHERE cm.memberId = :memberId
      ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC, c.id DESC
      """)
  List<ChatChannel> findMyChannels(@Param("memberId") Long memberId);

  /** DM pair UNIQUE 인덱스 단일 lookup (V17). caller는 min &lt; max로 정렬해 호출한다. */
  Optional<ChatChannel> findByDmMemberMinAndDmMemberMax(Long dmMemberMin, Long dmMemberMax);

  /**
   * @deprecated ADR-0012 v2 BE-2: {@link #findByDmMemberMinAndDmMemberMax} + UNIQUE 인덱스 사용. 본 쿼리는
   *     IN 서브쿼리 패턴으로 인덱스 활용 약함. FE-1 cleanup 후 BE-2.1에서 제거 예정.
   */
  @Deprecated(forRemoval = true)
  @Query(
      "SELECT c FROM ChatChannel c"
          + " WHERE c.type = 'DM'"
          + "   AND c.id IN (SELECT cm.channelId FROM ChatMember cm WHERE cm.memberId = :memberA)"
          + "   AND c.id IN (SELECT cm.channelId FROM ChatMember cm WHERE cm.memberId = :memberB)")
  Optional<ChatChannel> findDmChannel(
      @Param("memberA") Long memberA, @Param("memberB") Long memberB);

  /**
   * 마지막 메시지 monotonic guarded UPDATE.
   *
   * <p>현재 last_message_at이 NULL이거나, 새 메시지의 (createdAt, id)가 strictly greater일 때만 적용. 동시 INSERT 두
   * producer가 동시에 호출해도 최신 상태로만 수렴하고 stale로 후퇴하지 않는다.
   *
   * @return 영향받은 row 수 (0이면 새 메시지가 stale이라 무시됨, 정상)
   */
  @Modifying
  @Query(
      """
      UPDATE ChatChannel c
      SET c.lastMessageId = :msgId,
          c.lastMessageAt = :msgAt
      WHERE c.id = :channelId
        AND (c.lastMessageAt IS NULL
             OR c.lastMessageAt < :msgAt
             OR (c.lastMessageAt = :msgAt AND c.lastMessageId < :msgId))
      """)
  int updateLastMessage(
      @Param("channelId") Long channelId,
      @Param("msgId") Long msgId,
      @Param("msgAt") LocalDateTime msgAt);
}

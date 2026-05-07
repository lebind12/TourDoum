package com.ssafy.tourdoum.chat;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 채팅 메시지 JPA 레포지토리.
 *
 * <p>ADR-0012 v2 — keyset paging cursor `(created_at, id)`.
 *
 * <ul>
 *   <li>{@link #findOlder} backward (스크롤 업) — `(created_at, id) < cursor` ORDER BY created_at DESC,
 *       id DESC.
 *   <li>{@link #findNewer} forward (polling) — `(created_at, id) > cursor` ORDER BY ASC.
 *   <li>{@link #findRecent} 초기 페이지 — cursor 없이 최신 N건 (DESC).
 * </ul>
 *
 * <p>JPQL은 tuple comparison `(a,b) < (c,d)` 미지원 → OR 분해 형태 사용.
 *
 * <p>?sinceId 한시 호환은 기존 메서드를 그대로 둔다(BE-1 범위).
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

  /** legacy ?sinceId — newer-than-id polling. BE-1 한시 호환 유지. */
  List<ChatMessage> findTop50ByChannelIdAndIdGreaterThanOrderByIdAsc(Long channelId, Long sinceId);

  /** legacy 초기 로드 — sinceId=0 path. BE-1 한시 호환 유지. */
  List<ChatMessage> findTop50ByChannelIdOrderByIdDesc(Long channelId);

  /**
   * older 페이지(backward) — keyset cursor 미만의 메시지를 created_at/id 역순으로.
   *
   * <p>service 단계에서 ASC 정렬로 뒤집어 반환한다.
   */
  @Query(
      """
      SELECT m FROM ChatMessage m
      WHERE m.channelId = :channelId
        AND (m.createdAt < :cursorTs
             OR (m.createdAt = :cursorTs AND m.id < :cursorId))
      ORDER BY m.createdAt DESC, m.id DESC
      """)
  List<ChatMessage> findOlder(
      @Param("channelId") Long channelId,
      @Param("cursorTs") LocalDateTime beforeCreatedAt,
      @Param("cursorId") Long beforeId,
      Limit limit);

  /**
   * newer 페이지(forward, polling) — keyset cursor 초과 메시지를 ASC.
   *
   * <p>service 단계에서 그대로 반환한다.
   */
  @Query(
      """
      SELECT m FROM ChatMessage m
      WHERE m.channelId = :channelId
        AND (m.createdAt > :cursorTs
             OR (m.createdAt = :cursorTs AND m.id > :cursorId))
      ORDER BY m.createdAt ASC, m.id ASC
      """)
  List<ChatMessage> findNewer(
      @Param("channelId") Long channelId,
      @Param("cursorTs") LocalDateTime afterCreatedAt,
      @Param("cursorId") Long afterId,
      Limit limit);

  /** 초기 페이지(cursor 없음) — 최신 N건 DESC. service에서 ASC 뒤집기. */
  @Query(
      """
      SELECT m FROM ChatMessage m
      WHERE m.channelId = :channelId
      ORDER BY m.createdAt DESC, m.id DESC
      """)
  List<ChatMessage> findRecent(@Param("channelId") Long channelId, Limit limit);
}

package com.ssafy.tourdoum.chat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 채팅 서비스. 폴링 + keyset paging (ADR-0012 v2 BE-1). WebSocket/SSE 미사용. */
@Service
@Transactional(readOnly = true)
public class ChatService {

  /** ADR-0012 §3 LIMIT default. */
  public static final int DEFAULT_LIMIT = 20;

  /** ADR-0012 §3 LIMIT 상한 — clamp 1≤?≤50. */
  public static final int MAX_LIMIT = 50;

  private final ChatChannelRepository channelRepository;
  private final ChatMemberRepository memberRepository;
  private final ChatMessageRepository messageRepository;
  private final ChatDmCreator dmCreator;

  public ChatService(
      ChatChannelRepository channelRepository,
      ChatMemberRepository memberRepository,
      ChatMessageRepository messageRepository,
      ChatDmCreator dmCreator) {
    this.channelRepository = channelRepository;
    this.memberRepository = memberRepository;
    this.messageRepository = messageRepository;
    this.dmCreator = dmCreator;
  }

  /**
   * 내가 속한 채널 목록.
   *
   * @param memberId 로그인 회원 PK
   */
  public List<ChatChannelResponse> listMyChannels(Long memberId) {
    return channelRepository.findMyChannels(memberId).stream()
        .map(ChatChannelResponse::from)
        .toList();
  }

  /**
   * 채널 메시지 목록 — legacy `?sinceId=` 한시 호환 + 신규 `?afterCursor=` (forward polling).
   *
   * <p>ADR-0012 v2 §3: forward는 `afterCursor`가 우선. cursor가 있으면 sinceId 무시.
   *
   * @param channelId 채널 PK
   * @param memberId 로그인 회원 PK
   * @param afterCursor base64url JSON cursor (null/blank이면 sinceId fallback)
   * @param sinceId legacy. afterCursor가 있으면 무시
   * @param limitOrNull FE 요청 limit. null=default 20. clamp 1≤?≤50.
   */
  public ChatMessagePage messages(
      Long channelId, Long memberId, String afterCursor, long sinceId, Integer limitOrNull) {
    verifyMember(channelId, memberId);
    int limit = clampLimit(limitOrNull);

    if (afterCursor != null && !afterCursor.isBlank()) {
      ChatMessageCursor cursor = ChatMessageCursor.decodeOrNull(afterCursor);
      List<ChatMessage> rows =
          messageRepository.findNewer(
              channelId,
              LocalDateTime.ofInstant(cursor.createdAt(), ZoneOffset.UTC),
              cursor.id(),
              Limit.of(limit));
      return toAscPage(rows, limit, /* reverse= */ false);
    }

    if (sinceId <= 0) {
      // 초기 로드: 최신 limit건 DESC → ASC 뒤집기.
      List<ChatMessage> rows = messageRepository.findRecent(channelId, Limit.of(limit));
      return toAscPage(rows, limit, /* reverse= */ true);
    }

    // legacy sinceId path.
    List<ChatMessage> rows =
        messageRepository.findTop50ByChannelIdAndIdGreaterThanOrderByIdAsc(channelId, sinceId);
    if (rows.size() > limit) {
      rows = rows.subList(0, limit);
    }
    return toAscPage(rows, limit, /* reverse= */ false);
  }

  /**
   * older 페이지(backward, 스크롤 업) — ADR-0012 v2 §3 신규 endpoint.
   *
   * @param beforeCursor null/blank면 최신 페이지(초기 진입과 동일).
   */
  public ChatMessagePage messagesOlder(
      Long channelId, Long memberId, String beforeCursor, Integer limitOrNull) {
    verifyMember(channelId, memberId);
    int limit = clampLimit(limitOrNull);

    List<ChatMessage> rows;
    if (beforeCursor == null || beforeCursor.isBlank()) {
      rows = messageRepository.findRecent(channelId, Limit.of(limit));
    } else {
      ChatMessageCursor cursor = ChatMessageCursor.decodeOrNull(beforeCursor);
      rows =
          messageRepository.findOlder(
              channelId,
              LocalDateTime.ofInstant(cursor.createdAt(), ZoneOffset.UTC),
              cursor.id(),
              Limit.of(limit));
    }
    return toAscPage(rows, limit, /* reverse= */ true);
  }

  /**
   * 메시지 전송.
   *
   * @param channelId 채널 PK
   * @param memberId 송신자 PK
   * @param content 메시지 내용
   */
  @Transactional
  public ChatMessageResponse send(Long channelId, Long memberId, String content) {
    verifyMember(channelId, memberId);
    ChatMessage message =
        messageRepository.saveAndFlush(
            ChatMessage.builder().channelId(channelId).senderId(memberId).content(content).build());
    // ADR-0012 v2 BE-2: monotonic guarded UPDATE — race가 있어도 최신 상태로만 수렴.
    // 영향 row 0건은 stale로 후퇴 차단 — 정상.
    channelRepository.updateLastMessage(channelId, message.getId(), message.getCreatedAt());
    return ChatMessageResponse.from(message);
  }

  /**
   * DM 채널 열기 (없으면 자동 생성).
   *
   * <p>두 회원 간 DM 채널이 이미 있으면 기존 채널을 반환한다 (중복 방지).
   *
   * @param memberId 요청자 PK
   * @param otherMemberId 상대방 PK
   */
  @Transactional
  public ChatChannelResponse openDm(Long memberId, Long otherMemberId) {
    if (memberId.equals(otherMemberId)) {
      throw new IllegalArgumentException("자기 자신과 DM 채널을 열 수 없습니다.");
    }
    long min = Math.min(memberId, otherMemberId);
    long max = Math.max(memberId, otherMemberId);

    // 1. fast path — 기존 DM pair UNIQUE 인덱스 단일 lookup.
    Optional<ChatChannel> existing = channelRepository.findByDmMemberMinAndDmMemberMax(min, max);
    if (existing.isPresent()) {
      return ChatChannelResponse.from(existing.get());
    }

    // 2. 신규 — REQUIRES_NEW로 saveAndFlush. UNIQUE 위반은 동시 race가 이긴 쪽이 박아둔 상태.
    // 회수 시 InnoDB REPEATABLE READ로 외부 트랜잭션 snapshot이 winner의 commit을 못 보는 케이스가 있어
    // findFresh(REQUIRES_NEW)로 새 snapshot을 떠 재조회한다.
    try {
      return ChatChannelResponse.from(dmCreator.createOrFail(min, max));
    } catch (DataIntegrityViolationException dup) {
      return dmCreator.findFresh(min, max).map(ChatChannelResponse::from).orElseThrow(() -> dup);
    }
  }

  private void verifyMember(Long channelId, Long memberId) {
    if (!channelRepository.existsById(channelId)) {
      throw new ChatChannelNotFoundException(channelId);
    }
    if (!memberRepository.existsByChannelIdAndMemberId(channelId, memberId)) {
      throw new ChatForbiddenException(channelId);
    }
  }

  private static int clampLimit(Integer requested) {
    if (requested == null) {
      return DEFAULT_LIMIT;
    }
    if (requested < 1) {
      return 1;
    }
    return Math.min(requested, MAX_LIMIT);
  }

  /**
   * DESC/ASC 입력 → ASC 정렬 응답 페이지로 변환.
   *
   * <p>nextCursor 정책:
   *
   * <ul>
   *   <li>reverse=true(older/recent, DESC 입력): ASC 뒤집기 후 가장 첫 항목(가장 오래된)을 nextCursor로 — 다음 backward
   *       페이지 진입.
   *   <li>reverse=false(newer, ASC 입력): 마지막 항목(가장 최신)을 nextCursor로 — 다음 polling 진입.
   * </ul>
   *
   * <p>hasMore = (rows.size() == limit). 정확한 hasMore는 limit+1 fetch 후 trim하는 패턴이 표준이지만, 단순 휴리스틱으로
   * 충분(ADR §3 not strict). FE는 빈 응답을 받으면 hasMore=false 신뢰.
   */
  private static ChatMessagePage toAscPage(List<ChatMessage> rows, int limit, boolean reverse) {
    if (rows.isEmpty()) {
      return new ChatMessagePage(List.of(), null, limit, false);
    }
    List<ChatMessage> ordered = new ArrayList<>(rows);
    if (reverse) {
      Collections.reverse(ordered);
    } else {
      ordered.sort(
          Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId));
    }
    boolean hasMore = rows.size() == limit;
    String nextCursor = null;
    if (hasMore) {
      ChatMessage edge = reverse ? ordered.get(0) : ordered.get(ordered.size() - 1);
      nextCursor =
          new ChatMessageCursor(edge.getCreatedAt().toInstant(ZoneOffset.UTC), edge.getId())
              .encode();
    }
    return new ChatMessagePage(
        ordered.stream().map(ChatMessageResponse::from).toList(), nextCursor, limit, hasMore);
  }
}

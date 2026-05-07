package com.ssafy.tourdoum.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

/** ChatService 단위 테스트 (Mockito). 학습 친화 모드 — 양산 금지. */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  @Mock private ChatChannelRepository channelRepository;
  @Mock private ChatMemberRepository memberRepository;
  @Mock private ChatMessageRepository messageRepository;
  @Mock private ChatDmCreator dmCreator;

  @InjectMocks private ChatService chatService;

  @Test
  @DisplayName("openDm — fast path: findByDmMemberMinAndDmMemberMax 매치 시 즉시 반환")
  void openDm_returns_existing_via_pair_lookup() {
    long memberA = 5L;
    long memberB = 2L;
    ChatChannel existing =
        ChatChannel.builder()
            .name("DM:2:5")
            .type(ChatChannelType.DM)
            .dmMemberMin(2L)
            .dmMemberMax(5L)
            .build();
    given(channelRepository.findByDmMemberMinAndDmMemberMax(2L, 5L))
        .willReturn(Optional.of(existing));

    ChatChannelResponse response = chatService.openDm(memberA, memberB);

    assertThat(response.type()).isEqualTo(ChatChannelType.DM);
  }

  @Test
  @DisplayName("openDm — 신규 DM은 dmCreator.createOrFail로 위임 (REQUIRES_NEW)")
  void openDm_creates_new_via_dmCreator() {
    long memberA = 5L;
    long memberB = 2L;
    given(channelRepository.findByDmMemberMinAndDmMemberMax(2L, 5L)).willReturn(Optional.empty());
    ChatChannel created =
        ChatChannel.builder()
            .name("DM:2:5")
            .type(ChatChannelType.DM)
            .dmMemberMin(2L)
            .dmMemberMax(5L)
            .build();
    given(dmCreator.createOrFail(2L, 5L)).willReturn(created);

    ChatChannelResponse response = chatService.openDm(memberA, memberB);

    assertThat(response.type()).isEqualTo(ChatChannelType.DM);
  }

  @Test
  @DisplayName("openDm — DataIntegrityViolationException catch → re-find로 race winner를 반환")
  void openDm_recovers_from_unique_violation_via_refind() {
    long memberA = 5L;
    long memberB = 2L;
    ChatChannel raceWinner =
        ChatChannel.builder()
            .name("DM:2:5")
            .type(ChatChannelType.DM)
            .dmMemberMin(2L)
            .dmMemberMax(5L)
            .build();
    given(channelRepository.findByDmMemberMinAndDmMemberMax(2L, 5L)).willReturn(Optional.empty());
    given(dmCreator.createOrFail(2L, 5L))
        .willThrow(
            new org.springframework.dao.DataIntegrityViolationException(
                "uk_chat_channels_dm_pair"));
    // catch 분기는 dmCreator.findFresh(REQUIRES_NEW) 사용 — InnoDB snapshot 회피.
    given(dmCreator.findFresh(2L, 5L)).willReturn(Optional.of(raceWinner));

    ChatChannelResponse response = chatService.openDm(memberA, memberB);

    assertThat(response.type()).isEqualTo(ChatChannelType.DM);
  }

  @Test
  @DisplayName("send — saveAndFlush 후 channelRepository.updateLastMessage 호출")
  void send_invokes_guarded_update_last_message() {
    Long channelId = 10L;
    Long memberId = 1L;
    given(channelRepository.existsById(channelId)).willReturn(true);
    given(memberRepository.existsByChannelIdAndMemberId(channelId, memberId)).willReturn(true);
    ChatMessage saved = stub(7L, channelId, memberId, "hi", LocalDateTime.of(2026, 5, 8, 13, 0, 0));
    given(messageRepository.saveAndFlush(any(ChatMessage.class))).willReturn(saved);

    chatService.send(channelId, memberId, "hi");

    org.mockito.Mockito.verify(channelRepository)
        .updateLastMessage(eq(channelId), eq(7L), eq(saved.getCreatedAt()));
  }

  @Test
  @DisplayName("openDm — 자기 자신과 DM 시도 시 IllegalArgumentException 발생")
  void openDm_selfDm_throwsException() {
    // given
    Long memberId = 1L;

    // when / then
    assertThatThrownBy(() -> chatService.openDm(memberId, memberId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("자기 자신");
  }

  @Test
  @DisplayName("messagesOlder — cursor 미지정 + 멤버 OK → findRecent → ASC items + hasMore=true(꽉 찼을 때)")
  void messagesOlder_initial_returns_recent_asc() {
    Long channelId = 10L;
    Long memberId = 1L;
    given(channelRepository.existsById(channelId)).willReturn(true);
    given(memberRepository.existsByChannelIdAndMemberId(channelId, memberId)).willReturn(true);

    List<ChatMessage> recentDesc =
        List.of(
            stub(3L, channelId, memberId, "c", LocalDateTime.of(2026, 5, 8, 12, 0, 2)),
            stub(2L, channelId, memberId, "b", LocalDateTime.of(2026, 5, 8, 12, 0, 1)),
            stub(1L, channelId, memberId, "a", LocalDateTime.of(2026, 5, 8, 12, 0, 0)));
    given(messageRepository.findRecent(eq(channelId), any(Limit.class))).willReturn(recentDesc);

    ChatMessagePage page = chatService.messagesOlder(channelId, memberId, null, 3);

    assertThat(page.appliedLimit()).isEqualTo(3);
    assertThat(page.hasMore()).isTrue();
    assertThat(page.items()).extracting(ChatMessageResponse::id).containsExactly(1L, 2L, 3L);
    assertThat(page.nextCursor()).isNotNull();
  }

  @Test
  @DisplayName("messagesOlder — limit 51 요청은 50으로 clamp")
  void messagesOlder_limit_clamps_to_50() {
    Long channelId = 10L;
    Long memberId = 1L;
    given(channelRepository.existsById(channelId)).willReturn(true);
    given(memberRepository.existsByChannelIdAndMemberId(channelId, memberId)).willReturn(true);
    given(messageRepository.findRecent(eq(channelId), any(Limit.class))).willReturn(List.of());

    ChatMessagePage page = chatService.messagesOlder(channelId, memberId, null, 999);

    assertThat(page.appliedLimit()).isEqualTo(50);
    assertThat(page.hasMore()).isFalse();
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  @DisplayName("messagesOlder — beforeCursor 디코드된 값으로 findOlder 호출")
  void messagesOlder_with_before_cursor_calls_findOlder() {
    Long channelId = 10L;
    Long memberId = 1L;
    given(channelRepository.existsById(channelId)).willReturn(true);
    given(memberRepository.existsByChannelIdAndMemberId(channelId, memberId)).willReturn(true);

    LocalDateTime ts = LocalDateTime.of(2026, 5, 8, 12, 0, 5);
    String cursor = new ChatMessageCursor(ts.toInstant(ZoneOffset.UTC), 100L).encode();

    given(
            messageRepository.findOlder(
                eq(channelId), any(LocalDateTime.class), anyLong(), any(Limit.class)))
        .willReturn(List.of());

    ChatMessagePage page = chatService.messagesOlder(channelId, memberId, cursor, null);

    assertThat(page.appliedLimit()).isEqualTo(20); // default
    assertThat(page.items()).isEmpty();
    assertThat(page.hasMore()).isFalse();
  }

  @Test
  @DisplayName("messages(forward) — afterCursor 우선, sinceId 무시")
  void messages_forward_prefers_afterCursor_over_sinceId() {
    Long channelId = 10L;
    Long memberId = 1L;
    given(channelRepository.existsById(channelId)).willReturn(true);
    given(memberRepository.existsByChannelIdAndMemberId(channelId, memberId)).willReturn(true);

    String cursor = new ChatMessageCursor(Instant.parse("2026-05-08T12:00:00Z"), 5L).encode();

    given(
            messageRepository.findNewer(
                eq(channelId), any(LocalDateTime.class), anyLong(), any(Limit.class)))
        .willReturn(
            List.of(stub(6L, channelId, memberId, "x", LocalDateTime.of(2026, 5, 8, 12, 0, 1))));

    ChatMessagePage page = chatService.messages(channelId, memberId, cursor, 999L, 10);

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).id()).isEqualTo(6L);
    assertThat(page.hasMore()).isFalse();
  }

  /** 짧은 stub helper — id/createdAt 강제 주입을 위해 reflection 없이 builder + setter 못 쓰니 inline copy. */
  private static ChatMessage stub(
      Long id, Long channelId, Long senderId, String content, LocalDateTime createdAt) {
    ChatMessage m =
        ChatMessage.builder().channelId(channelId).senderId(senderId).content(content).build();
    try {
      var idField = ChatMessage.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(m, id);
      var cField = ChatMessage.class.getDeclaredField("createdAt");
      cField.setAccessible(true);
      cField.set(m, createdAt);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    return m;
  }
}

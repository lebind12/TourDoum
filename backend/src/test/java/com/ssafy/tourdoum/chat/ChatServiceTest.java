package com.ssafy.tourdoum.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ChatService 단위 테스트 (Mockito). 학습 친화 모드 — 양산 금지. */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  @Mock private ChatChannelRepository channelRepository;
  @Mock private ChatMemberRepository memberRepository;
  @Mock private ChatMessageRepository messageRepository;

  @InjectMocks private ChatService chatService;

  @Test
  @DisplayName("openDm — 기존 DM 채널이 없으면 새 채널을 자동 생성하고 두 멤버를 추가한다")
  void openDm_createsNewChannel_whenNotExists() {
    // given
    Long memberA = 1L;
    Long memberB = 2L;

    given(channelRepository.findDmChannel(memberA, memberB)).willReturn(Optional.empty());

    ChatChannel savedChannel =
        ChatChannel.builder()
            .name("DM:" + memberA + ":" + memberB)
            .type(ChatChannelType.DM)
            .build();
    given(channelRepository.save(any(ChatChannel.class))).willReturn(savedChannel);
    given(memberRepository.save(any(ChatMember.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    ChatChannelResponse response = chatService.openDm(memberA, memberB);

    // then
    assertThat(response).isNotNull();
    assertThat(response.type()).isEqualTo(ChatChannelType.DM);
  }

  @Test
  @DisplayName("openDm — 기존 DM 채널이 있으면 기존 채널을 반환한다 (중복 생성 방지)")
  void openDm_returnsExistingChannel_whenAlreadyExists() {
    // given
    Long memberA = 1L;
    Long memberB = 2L;

    ChatChannel existing =
        ChatChannel.builder()
            .name("DM:" + memberA + ":" + memberB)
            .type(ChatChannelType.DM)
            .build();
    given(channelRepository.findDmChannel(memberA, memberB)).willReturn(Optional.of(existing));

    // when
    ChatChannelResponse response = chatService.openDm(memberA, memberB);

    // then
    assertThat(response).isNotNull();
    assertThat(response.type()).isEqualTo(ChatChannelType.DM);
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
}

package com.ssafy.tourdoum.chat;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 채팅 서비스. 폴링 기반 — WebSocket/SSE 미사용. */
@Service
@Transactional(readOnly = true)
public class ChatService {

  private final ChatChannelRepository channelRepository;
  private final ChatMemberRepository memberRepository;
  private final ChatMessageRepository messageRepository;

  public ChatService(
      ChatChannelRepository channelRepository,
      ChatMemberRepository memberRepository,
      ChatMessageRepository messageRepository) {
    this.channelRepository = channelRepository;
    this.memberRepository = memberRepository;
    this.messageRepository = messageRepository;
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
   * 채널 메시지 목록 (sinceId 이후).
   *
   * <p>sinceId=0 (또는 null) 이면 최근 50건을 역순 → 정순으로 반환.
   *
   * @param channelId 채널 PK
   * @param memberId 로그인 회원 PK (구성원 여부 체크)
   * @param sinceId 마지막으로 받은 메시지 ID (0 = 전체)
   */
  public List<ChatMessageResponse> messages(Long channelId, Long memberId, long sinceId) {
    verifyMember(channelId, memberId);

    if (sinceId <= 0) {
      return messageRepository.findTop50ByChannelIdOrderByIdDesc(channelId).stream()
          .sorted(Comparator.comparingLong(ChatMessage::getId))
          .map(ChatMessageResponse::from)
          .toList();
    }
    return messageRepository
        .findTop50ByChannelIdAndIdGreaterThanOrderByIdAsc(channelId, sinceId)
        .stream()
        .map(ChatMessageResponse::from)
        .toList();
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
        messageRepository.save(
            ChatMessage.builder().channelId(channelId).senderId(memberId).content(content).build());
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

    return channelRepository
        .findDmChannel(memberId, otherMemberId)
        .map(ChatChannelResponse::from)
        .orElseGet(
            () -> {
              ChatChannel channel =
                  channelRepository.save(
                      ChatChannel.builder()
                          .name("DM:" + memberId + ":" + otherMemberId)
                          .type(ChatChannelType.DM)
                          .build());
              memberRepository.save(
                  ChatMember.builder().channelId(channel.getId()).memberId(memberId).build());
              memberRepository.save(
                  ChatMember.builder().channelId(channel.getId()).memberId(otherMemberId).build());
              return ChatChannelResponse.from(channel);
            });
  }

  private void verifyMember(Long channelId, Long memberId) {
    if (!channelRepository.existsById(channelId)) {
      throw new ChatChannelNotFoundException(channelId);
    }
    if (!memberRepository.existsByChannelIdAndMemberId(channelId, memberId)) {
      throw new ChatForbiddenException(channelId);
    }
  }
}

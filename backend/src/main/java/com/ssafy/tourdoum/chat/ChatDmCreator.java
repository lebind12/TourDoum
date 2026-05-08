package com.ssafy.tourdoum.chat;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * DM 채널 생성 — REQUIRES_NEW 트랜잭션.
 *
 * <p>ADR-0012 v2 §openDm 동시성: 두 사용자가 동시에 DM을 열면 UNIQUE 인덱스 {@code uk_chat_channels_dm_pair}가 race를
 * DB level에서 차단한다. 한 쪽은 {@link org.springframework.dao.DataIntegrityViolationException}을 받고,
 * caller는 그 예외를 catch한 뒤 기존 채널을 re-find한다.
 *
 * <p>REQUIRES_NEW로 분리한 이유: unique violation으로 트랜잭션이 rollback-only로 표시되면 같은 트랜잭션 안에서 후속 SELECT가 깨진다.
 * 외부 트랜잭션을 보존하기 위해 별 component + 별 트랜잭션.
 *
 * <p>self-injection 함정 회피: ChatService에 같은 메서드를 두면 self-call이 트랜잭션 경계를 못 넘어 REQUIRES_NEW가 무력화된다. 별
 * component 외부 호출이라 정상 동작.
 */
@Component
public class ChatDmCreator {

  private final ChatChannelRepository channelRepository;
  private final ChatMemberRepository memberRepository;

  public ChatDmCreator(
      ChatChannelRepository channelRepository, ChatMemberRepository memberRepository) {
    this.channelRepository = channelRepository;
    this.memberRepository = memberRepository;
  }

  /**
   * 새 DM 채널 + 두 멤버를 새 트랜잭션에서 박는다. UNIQUE 위반 시 {@link
   * org.springframework.dao.DataIntegrityViolationException}.
   *
   * @param dmMin 두 멤버 중 작은 ID
   * @param dmMax 두 멤버 중 큰 ID (dmMin &lt; dmMax 가 호출자 책임)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ChatChannel createOrFail(long dmMin, long dmMax) {
    ChatChannel saved =
        channelRepository.saveAndFlush(
            ChatChannel.builder()
                .name("DM:" + dmMin + ":" + dmMax)
                .type(ChatChannelType.DM)
                .dmMemberMin(dmMin)
                .dmMemberMax(dmMax)
                .build());
    memberRepository.save(ChatMember.builder().channelId(saved.getId()).memberId(dmMin).build());
    memberRepository.save(ChatMember.builder().channelId(saved.getId()).memberId(dmMax).build());
    return saved;
  }

  /**
   * race winner의 채널을 fresh snapshot으로 재조회한다.
   *
   * <p>{@link Propagation#REQUIRES_NEW} 새 트랜잭션이 InnoDB REPEATABLE READ snapshot을 새로 떠 winner의
   * commit을 즉시 본다. 외부 transaction snapshot에서는 winner의 row가 보이지 않을 수 있어 catch 분기에서 이 메서드를 사용한다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public Optional<ChatChannel> findFresh(long dmMin, long dmMax) {
    return channelRepository.findByDmMemberMinAndDmMemberMax(dmMin, dmMax);
  }
}

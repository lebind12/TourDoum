package com.ssafy.tourdoum.chat;

import com.ssafy.tourdoum.member.MemberSignedUpEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * dev profile 한정 — 신규 signup 회원을 seed PUBLIC 채널 ({@link #SEED_PUBLIC_NAME})에 자동 join.
 *
 * <p>배경: BE-3 seed runner가 만든 PUBLIC 채널은 placeholder bcrypt 시드 user들로 채워져 있어 직접 로그인 불가.
 * QA #30 Scenario B(폴링) 검증에 필요한 "실 로그인 가능한 user가 seed PUBLIC 채널에 속한 상태"를 만들기 위해
 * dev에선 가입과 동시에 멤버십을 INSERT 한다. PUBLIC join 정책이 운영 단계에서 확정될 때까지의 임시 처방.
 *
 * <p>profile 가드: 빈 자체가 {@code @Profile("dev")} → 운영 영향 0. seed 채널 부재 시 silent no-op(debug 로그).
 *
 * <p>트랜잭션 경계: {@code @TransactionalEventListener(AFTER_COMMIT)} — signup 트랜잭션 커밋 후에만 실행.
 * 본 listener의 실패는 가입 자체에 영향 없음. INSERT 자체는 새 트랜잭션({@link Propagation#REQUIRES_NEW}).
 */
@Component
@Profile("dev")
public class ChatPublicAutoJoinListener {

  private static final Logger LOG = LoggerFactory.getLogger(ChatPublicAutoJoinListener.class);

  /** ADR-0012 v2 seed sentinel — {@code ChatSeedingService.PUBLIC_SENTINEL}와 동일해야 한다. */
  static final String SEED_PUBLIC_NAME = "seed-public-1";

  private final ChatChannelRepository channelRepository;
  private final ChatMemberRepository memberRepository;

  public ChatPublicAutoJoinListener(
      ChatChannelRepository channelRepository, ChatMemberRepository memberRepository) {
    this.channelRepository = channelRepository;
    this.memberRepository = memberRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onSignedUp(MemberSignedUpEvent event) {
    Long channelId = findSeedPublicChannelId();
    if (channelId == null) {
      LOG.debug(
          "[chat-autojoin/dev] seed PUBLIC channel '{}' 부재 — memberId={} silent no-op.",
          SEED_PUBLIC_NAME,
          event.memberId());
      return;
    }
    if (memberRepository.existsByChannelIdAndMemberId(channelId, event.memberId())) {
      return; // idempotent
    }
    try {
      memberRepository.save(
          ChatMember.builder().channelId(channelId).memberId(event.memberId()).build());
      LOG.info(
          "[chat-autojoin/dev] memberId={} → channelId={} ({}) joined.",
          event.memberId(),
          channelId,
          SEED_PUBLIC_NAME);
    } catch (RuntimeException ex) {
      // 운영 영향 0이지만 PK 경합/race로 dup 발생 가능 — 무시.
      LOG.warn(
          "[chat-autojoin/dev] join 실패 — memberId={} channelId={}: {}",
          event.memberId(),
          channelId,
          ex.getMessage());
    }
  }

  private Long findSeedPublicChannelId() {
    return channelRepository.findByName(SEED_PUBLIC_NAME).map(ChatChannel::getId).orElse(null);
  }
}

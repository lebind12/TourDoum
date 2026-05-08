package com.ssafy.tourdoum.member;

/**
 * 회원가입 완료 이벤트. {@link MemberService#signup}에서 트랜잭션 커밋 직전에 publish 되며 listener는
 * 보통 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 후속 비핵심 작업(예:
 * dev 채팅 채널 auto-join, welcome notification 등)을 처리한다.
 *
 * <p>핵심 가입 흐름은 {@link MemberService#signup}에서 완결되어야 하며, 본 이벤트의 listener가 실패해도 가입 자체는
 * 영향받지 않는다(트랜잭션 분리).
 */
public record MemberSignedUpEvent(Long memberId, String email, String nickname) {}

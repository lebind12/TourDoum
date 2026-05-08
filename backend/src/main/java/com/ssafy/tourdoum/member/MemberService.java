package com.ssafy.tourdoum.member;

import com.ssafy.tourdoum.auth.PasswordPolicyValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 비즈니스 로직. */
@Service
@Transactional(readOnly = true)
public class MemberService {

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicy;
  private final ApplicationEventPublisher eventPublisher;

  public MemberService(
      MemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicyValidator passwordPolicy,
      ApplicationEventPublisher eventPublisher) {
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.eventPublisher = eventPublisher;
  }

  /**
   * 회원가입.
   *
   * @param request 회원가입 요청 DTO
   * @return 저장된 Member 엔티티
   * @throws DuplicateEmailException 이메일 중복 시
   * @throws DuplicateNicknameException 닉네임 중복 시
   */
  @Transactional
  public Member signup(SignupRequest request) {
    if (memberRepository.existsByEmail(request.email())) {
      throw new DuplicateEmailException(request.email());
    }
    if (memberRepository.existsByNickname(request.nickname())) {
      throw new DuplicateNicknameException(request.nickname());
    }
    // ADR-0011 BE-4.2: 비밀번호 정책 검증.
    passwordPolicy.validate(request.password(), request.email(), request.nickname());

    Member member =
        Member.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .nickname(request.nickname())
            .role(MemberRole.ROLE_USER)
            .build();

    Member saved = memberRepository.save(member);
    // BE-1.1: 가입 이벤트 publish — listener는 트랜잭션 커밋 이후(AFTER_COMMIT)에 후속 작업.
    // 핵심 가입 흐름과 분리되며, listener 실패는 가입 결과에 영향 없음.
    eventPublisher.publishEvent(
        new MemberSignedUpEvent(saved.getId(), saved.getEmail(), saved.getNickname()));
    return saved;
  }
}

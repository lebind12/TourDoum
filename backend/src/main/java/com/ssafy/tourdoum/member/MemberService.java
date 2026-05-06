package com.ssafy.tourdoum.member;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 비즈니스 로직. */
@Service
@Transactional(readOnly = true)
public class MemberService {

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;

  public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
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

    Member member =
        Member.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .nickname(request.nickname())
            .role(MemberRole.ROLE_USER)
            .build();

    return memberRepository.save(member);
  }
}

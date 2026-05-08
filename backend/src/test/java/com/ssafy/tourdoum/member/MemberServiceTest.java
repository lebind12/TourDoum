package com.ssafy.tourdoum.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.ssafy.tourdoum.auth.PasswordPolicyValidator;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock private MemberRepository memberRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private PasswordPolicyValidator passwordPolicy;
  @InjectMocks private MemberService memberService;

  @Test
  @DisplayName("정상 회원가입 - 저장 검증")
  void signup_success() {
    // given
    SignupRequest request = new SignupRequest("test@example.com", "Str0ngPass!2026", "tester");
    given(memberRepository.existsByEmail(request.email())).willReturn(false);
    given(memberRepository.existsByNickname(request.nickname())).willReturn(false);
    given(passwordEncoder.encode(request.password())).willReturn("encoded_password");

    Member savedMember =
        Member.builder()
            .email(request.email())
            .password("encoded_password")
            .nickname(request.nickname())
            .role(MemberRole.ROLE_USER)
            .build();
    given(memberRepository.save(any(Member.class))).willReturn(savedMember);

    // when
    Member result = memberService.signup(request);

    // then
    assertThat(result.getEmail()).isEqualTo("test@example.com");
    assertThat(result.getNickname()).isEqualTo("tester");
    assertThat(result.getRole()).isEqualTo(MemberRole.ROLE_USER);
    verify(memberRepository).save(any(Member.class));
  }

  @Test
  @DisplayName("이메일 중복 - DuplicateEmailException 발생")
  void signup_duplicate_email() {
    // given
    SignupRequest request = new SignupRequest("dup@example.com", "Str0ngPass!2026", "nick");
    given(memberRepository.existsByEmail(request.email())).willReturn(true);

    // when / then
    assertThatThrownBy(() -> memberService.signup(request))
        .isInstanceOf(DuplicateEmailException.class)
        .hasMessageContaining("dup@example.com");
  }
}

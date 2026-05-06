package com.ssafy.tourdoum.auth;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 관련 API. POST /api/auth/login, POST /api/auth/logout 은 SecurityConfig 필터/핸들러 처리. */
@RestController
@RequestMapping("/api")
public class AuthController {

  private final MemberRepository memberRepository;

  public AuthController(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  /** GET /api/me — 현재 로그인한 회원 정보. 인증 필요. */
  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal UserDetails userDetails) {
    Member member =
        memberRepository
            .findByEmail(userDetails.getUsername())
            .orElseThrow(
                () -> new UsernameNotFoundException("회원 없음: " + userDetails.getUsername()));
    return MeResponse.from(member);
  }
}

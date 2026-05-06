package com.ssafy.tourdoum.auth;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Spring Security UserDetailsService 구현 — DB에서 회원 조회 후 UserDetails 반환. */
@Service
public class MemberDetailsService implements UserDetailsService {

  private final MemberRepository memberRepository;

  public MemberDetailsService(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다: " + email));

    return User.withUsername(member.getEmail())
        .password(member.getPassword())
        .authorities(new SimpleGrantedAuthority(member.getRole().name()))
        .build();
  }
}

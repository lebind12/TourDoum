package com.ssafy.tourdoum.auth;

import com.ssafy.tourdoum.member.Member;

/** GET /api/me 응답 DTO. */
public record MeResponse(Long id, String email, String nickname, String role) {

  public static MeResponse from(Member member) {
    return new MeResponse(
        member.getId(), member.getEmail(), member.getNickname(), member.getRole().name());
  }
}

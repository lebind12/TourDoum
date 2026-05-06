package com.ssafy.tourdoum.member;

/** 회원가입 응답 DTO. */
public record SignupResponse(Long id, String email, String nickname) {

  public static SignupResponse from(Member member) {
    return new SignupResponse(member.getId(), member.getEmail(), member.getNickname());
  }
}

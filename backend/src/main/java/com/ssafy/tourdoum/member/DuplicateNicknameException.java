package com.ssafy.tourdoum.member;

/** 닉네임 중복 예외. */
public class DuplicateNicknameException extends RuntimeException {

  public DuplicateNicknameException(String nickname) {
    super("이미 사용 중인 닉네임입니다: " + nickname);
  }
}

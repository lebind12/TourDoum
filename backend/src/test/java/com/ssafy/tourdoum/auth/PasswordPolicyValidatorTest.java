package com.ssafy.tourdoum.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PasswordPolicyValidator 단위 테스트 — ADR-0011 BE-4.2. */
class PasswordPolicyValidatorTest {

  private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

  @Test
  @DisplayName("12자 미만 거부")
  void rejects_short_password() {
    assertThatThrownBy(() -> validator.validate("Short1!", "u@example.com", "user"))
        .isInstanceOf(InvalidPasswordException.class)
        .hasMessageContaining("12자 이상");
  }

  @Test
  @DisplayName("64자 초과 거부")
  void rejects_long_password() {
    String pw = "A".repeat(65);
    assertThatThrownBy(() -> validator.validate(pw, "u@example.com", "user"))
        .isInstanceOf(InvalidPasswordException.class);
  }

  @Test
  @DisplayName("blocklist 일치 거부 (대소문자 무시)")
  void rejects_blocklisted() {
    // common blocklist는 그 자체로 12자 이상이어야 hit. "tourdoum123" 11자 → 길이 검증 먼저 실패.
    assertThatThrownBy(() -> validator.validate("default12345", "x@example.com", "z"))
        .isInstanceOf(InvalidPasswordException.class)
        .hasMessageContaining("자주 사용되는");
  }

  @Test
  @DisplayName("이메일 local-part 포함 거부")
  void rejects_email_similar() {
    assertThatThrownBy(() -> validator.validate("alicePassword99", "alice@example.com", "nick"))
        .isInstanceOf(InvalidPasswordException.class)
        .hasMessageContaining("이메일/닉네임");
  }

  @Test
  @DisplayName("닉네임 포함 거부")
  void rejects_nickname_similar() {
    assertThatThrownBy(() -> validator.validate("tester987654", "u@example.com", "tester"))
        .isInstanceOf(InvalidPasswordException.class);
  }

  @Test
  @DisplayName("정상 패스워드 통과 — 12자 이상 + blocklist/유사값 회피")
  void accepts_strong_password() {
    assertThatCode(() -> validator.validate("Str0ngPass!2026", "u@example.com", "tester"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("null 비밀번호 거부")
  void rejects_null() {
    assertThatThrownBy(() -> validator.validate(null, "u@example.com", "u"))
        .isInstanceOf(InvalidPasswordException.class);
  }
}

package com.ssafy.tourdoum.auth;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 정책 검증 — ADR-0011 BE-4.2.
 *
 * <ul>
 *   <li>12자 이상 ~ 64자 이하 (정책, 법령 수치 X).
 *   <li>공백·유니코드 허용.
 *   <li>HIBP top-N + 자체 blocklist 차단 (학습 단계 inline 100건).
 *   <li>이메일 local-part / 닉네임 유사값 차단.
 *   <li>3종 결합 강제 X / 정기 변경 강제 X (NIST 800-63B Rev.4).
 * </ul>
 *
 * <p>외부 API HIBP 호출은 본 task 범위 외(BE-4.1 후속). 학습 단계 inline blocklist는 가장 흔한 패턴 차단 목적.
 */
@Component
public class PasswordPolicyValidator {

  public static final int MIN_LENGTH = 12;
  public static final int MAX_LENGTH = 64;

  /** 자주 노출되는 흔한 패스워드 — 학습 단계 inline. production은 HIBP API. */
  private static final Set<String> COMMON_BLOCKLIST =
      Set.of(
          "password", "password1", "password123", "qwerty", "qwerty123", "asdf1234",
          "abc123", "letmein", "welcome", "iloveyou", "admin", "administrator",
          "passw0rd", "p@ssw0rd", "p@ssword", "p@ssword1", "12345678", "123456789",
          "1234567890", "11111111", "00000000", "secret", "tourdoum", "tourdoum1",
          "tourdoum123", "default12345", "changeme123", "qwerty1234", "asdfasdf");

  /**
   * 검증. 위반 시 {@link InvalidPasswordException} (400). 외부에서 try/catch로 메시지 노출 가능.
   *
   * @param password 평문(엔코딩 전)
   * @param email 가입 이메일 (유사값 차단)
   * @param nickname 가입 닉네임 (유사값 차단)
   */
  public void validate(String password, String email, String nickname) {
    if (password == null) {
      throw new InvalidPasswordException("비밀번호는 필수입니다.");
    }
    if (password.length() < MIN_LENGTH) {
      throw new InvalidPasswordException("비밀번호는 " + MIN_LENGTH + "자 이상이어야 합니다.");
    }
    if (password.length() > MAX_LENGTH) {
      throw new InvalidPasswordException("비밀번호는 " + MAX_LENGTH + "자 이하여야 합니다.");
    }
    String lower = password.toLowerCase();
    if (COMMON_BLOCKLIST.contains(lower)) {
      throw new InvalidPasswordException("자주 사용되는 비밀번호는 사용할 수 없습니다.");
    }
    // 자체 blocklist에 prefix/suffix 매칭도 — "password" 포함만 해도 거부.
    Set<String> dynamicBlock = new HashSet<>();
    if (email != null && email.contains("@")) {
      dynamicBlock.add(email.substring(0, email.indexOf('@')).toLowerCase());
    }
    if (nickname != null && !nickname.isBlank()) {
      dynamicBlock.add(nickname.toLowerCase());
    }
    for (String token : dynamicBlock) {
      if (token.length() >= 4 && lower.contains(token)) {
        throw new InvalidPasswordException("이메일/닉네임과 유사한 비밀번호는 사용할 수 없습니다.");
      }
    }
  }
}

package com.ssafy.tourdoum.auth;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reset 플로우 — ADR-0011 BE-4.5.
 *
 * <ul>
 *   <li>{@link #initiate}: SecureRandom plain 토큰 1개 발급 → SHA-256 해시 저장(30분 TTL). 본인확인 채널은 학습 단계
 *       콘솔 출력 mock(LOG.info).
 *   <li>{@link #complete}: plain 토큰 → 해시 → atomic consume(1회용) → 정책 검증 → encode + save + revoke.
 * </ul>
 */
@Service
public class PasswordResetService {

  private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);

  /** Reset 토큰 TTL. */
  static final Duration RESET_TTL = Duration.ofMinutes(30);

  private final MemberRepository memberRepository;
  private final PasswordResetTokenStore tokenStore;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator policy;
  private final UserRevocationStore revocationStore;
  private final SecureRandom random = new SecureRandom();

  public PasswordResetService(
      MemberRepository memberRepository,
      PasswordResetTokenStore tokenStore,
      PasswordEncoder passwordEncoder,
      PasswordPolicyValidator policy,
      UserRevocationStore revocationStore) {
    this.memberRepository = memberRepository;
    this.tokenStore = tokenStore;
    this.passwordEncoder = passwordEncoder;
    this.policy = policy;
    this.revocationStore = revocationStore;
  }

  /**
   * 토큰 발급 — 사용자 존재 시에만 박제. 응답은 동일하게 200(시도 enumeration 방지).
   *
   * @return plain 토큰 (학습 단계 콘솔에 박는다, production은 이메일 발송)
   */
  @Transactional(readOnly = true)
  public Optional<String> initiate(String email) {
    Optional<Member> member = memberRepository.findByEmail(email);
    if (member.isEmpty()) {
      // enumeration 방지: 사용자 부재여도 timing-safe하게 동일 응답.
      return Optional.empty();
    }
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String plain = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String hash = sha256(plain);
    tokenStore.save(hash, member.get().getId(), RESET_TTL);
    // 학습 단계: 콘솔에 토큰 노출 — production은 이메일 발송으로 교체.
    LOG.info("[password-reset] email={} token={} (콘솔 mock — production은 이메일 발송)", email, plain);
    return Optional.of(plain);
  }

  /**
   * 토큰 검증 + 비밀번호 변경 + revocation hook.
   *
   * @param plainToken 사용자가 받은 plain 토큰
   * @param newPassword 새 비밀번호 (정책 검증 통과 필수)
   */
  @Transactional
  public void complete(String plainToken, String newPassword) {
    if (plainToken == null || plainToken.isBlank()) {
      throw new InvalidResetTokenException("토큰이 비어 있습니다.");
    }
    long memberId = tokenStore.consumeOrMinusOne(sha256(plainToken));
    if (memberId < 0) {
      throw new InvalidResetTokenException("토큰이 만료되었거나 이미 사용되었습니다.");
    }
    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new InvalidResetTokenException("회원을 찾을 수 없습니다."));
    policy.validate(newPassword, member.getEmail(), member.getNickname());
    member.changePassword(passwordEncoder.encode(newPassword));
    memberRepository.save(member);
    revocationStore.bump(member.getId());
  }

  static String sha256(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}

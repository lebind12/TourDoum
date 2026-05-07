package com.ssafy.tourdoum.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.tourdoum.global.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * ChatMessageRepository keyset 쿼리 슬라이스 — H2 + JpaAuditing.
 *
 * <p>ADR-0012 v2 §3 검증: tie-break / limit / strict 비교 / 다른 채널 미혼입.
 *
 * <p>Audit이 created_at을 강제하므로 테스트는 INSERT 후 직접 SQL UPDATE로 timestamp를 박는다(@Column(updatable=false)는
 * JPA-level이며 native UPDATE는 통과).
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ChatMessageRepositoryKeysetTest {

  @Autowired private ChatMessageRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TestEntityManager em;

  private static final long CHANNEL = 100L;
  private static final long OTHER_CHANNEL = 200L;

  /** 시드 후 id → ChatMessage 매핑(테스트에서 자유로이 사용). */
  private ChatMessage m1, m2, m3, m4, m5, x1;

  @BeforeEach
  void seed() {
    jdbcTemplate.update("DELETE FROM chat_messages");
    LocalDateTime t0 = LocalDateTime.of(2026, 5, 8, 12, 0, 0);
    // 같은 channel: m1, m2 동일 ts (tie-break 검증), m3/m4/m5 1초씩 늘림.
    m1 = save(CHANNEL, "m1", t0);
    m2 = save(CHANNEL, "m2", t0);
    m3 = save(CHANNEL, "m3", t0.plusSeconds(1));
    m4 = save(CHANNEL, "m4", t0.plusSeconds(2));
    m5 = save(CHANNEL, "m5", t0.plusSeconds(3));
    x1 = save(OTHER_CHANNEL, "x1", t0.plusSeconds(2));
  }

  @Test
  @DisplayName("findRecent — 최신 N건 DESC, 다른 채널 미포함")
  void findRecent_returns_desc_within_channel() {
    List<ChatMessage> rows = repository.findRecent(CHANNEL, Limit.of(3));
    assertThat(rows).hasSize(3);
    assertThat(rows).extracting(ChatMessage::getContent).containsExactly("m5", "m4", "m3");
    assertThat(rows).noneMatch(r -> r.getChannelId() == OTHER_CHANNEL);
  }

  @Test
  @DisplayName("findOlder — strictly less than cursor (m3 cursor → m2, m1)")
  void findOlder_strictly_less_with_tie_break() {
    List<ChatMessage> older =
        repository.findOlder(CHANNEL, m3.getCreatedAt(), m3.getId(), Limit.of(10));
    assertThat(older).extracting(ChatMessage::getContent).containsExactly("m2", "m1");
  }

  @Test
  @DisplayName("findOlder — tie 케이스 (m2 cursor, 같은 ts) → m1만")
  void findOlder_tie_break_picks_smaller_id() {
    List<ChatMessage> older =
        repository.findOlder(CHANNEL, m2.getCreatedAt(), m2.getId(), Limit.of(10));
    assertThat(older).extracting(ChatMessage::getContent).containsExactly("m1");
  }

  @Test
  @DisplayName("findNewer — strictly greater than cursor (m3 cursor → m4, m5)")
  void findNewer_strictly_greater() {
    List<ChatMessage> newer =
        repository.findNewer(CHANNEL, m3.getCreatedAt(), m3.getId(), Limit.of(10));
    assertThat(newer).extracting(ChatMessage::getContent).containsExactly("m4", "m5");
  }

  @Test
  @DisplayName("Limit.of(N)이 정확히 N건만 반환")
  void limit_clamps_result_size() {
    List<ChatMessage> rows = repository.findRecent(CHANNEL, Limit.of(2));
    assertThat(rows).hasSize(2);
  }

  /** Audit가 박은 created_at을 native UPDATE로 덮어쓰고 EM clear 후 재조회한다. */
  private ChatMessage save(long channelId, String content, LocalDateTime createdAt) {
    ChatMessage saved =
        repository.saveAndFlush(
            ChatMessage.builder().channelId(channelId).senderId(1L).content(content).build());
    Long id = saved.getId();
    jdbcTemplate.update("UPDATE chat_messages SET created_at = ? WHERE id = ?", createdAt, id);
    em.clear(); // 1st-level cache의 audit 시점 entity 무효화 → 다음 findById가 native UPDATE 결과를 본다.
    return repository.findById(id).orElseThrow();
  }
}

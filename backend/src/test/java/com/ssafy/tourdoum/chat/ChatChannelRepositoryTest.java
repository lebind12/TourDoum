package com.ssafy.tourdoum.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.tourdoum.global.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * ChatChannelRepository 슬라이스 — H2 + JPA + Auditing.
 *
 * <p>ADR-0012 v2 BE-2 검증:
 *
 * <ul>
 *   <li>{@code findMyChannels} JOIN + 정렬(last_message_at DESC NULLS LAST 흉내).
 *   <li>{@code findByDmMemberMinAndDmMemberMax} pair 단일 lookup.
 *   <li>{@code updateLastMessage} guarded — stale 시도는 0 row affected.
 * </ul>
 *
 * <p>UNIQUE/CHECK constraint는 H2 ddl-auto 미적용(ChatChannel 엔티티에 박지 않았음). MySQL 적용은 IT.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ChatChannelRepositoryTest {

  @Autowired private ChatChannelRepository channelRepository;
  @Autowired private ChatMemberRepository memberRepository;
  @Autowired private TestEntityManager em;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("findMyChannels — JOIN 패턴, last_message_at DESC NULLS LAST(=createdAt fallback)")
  void findMyChannels_joins_and_orders_by_last_message_at_desc() {
    long member = 1L;
    // ch1: last_message_at = T+10
    ChatChannel c1 =
        save(
            ChatChannel.builder().name("ch1").type(ChatChannelType.PUBLIC).build(),
            null,
            LocalDateTime.of(2026, 5, 8, 13, 0, 10),
            999L);
    // ch2: last_message_at = NULL → createdAt fallback (오래됨)
    ChatChannel c2 =
        save(
            ChatChannel.builder().name("ch2").type(ChatChannelType.PUBLIC).build(),
            null,
            null,
            null);
    // ch3: last_message_at = T+20 (가장 최신)
    ChatChannel c3 =
        save(
            ChatChannel.builder().name("ch3").type(ChatChannelType.PUBLIC).build(),
            null,
            LocalDateTime.of(2026, 5, 8, 13, 0, 20),
            1234L);
    memberRepository.save(ChatMember.builder().channelId(c1.getId()).memberId(member).build());
    memberRepository.save(ChatMember.builder().channelId(c2.getId()).memberId(member).build());
    memberRepository.save(ChatMember.builder().channelId(c3.getId()).memberId(member).build());
    em.flush();

    List<ChatChannel> rows = channelRepository.findMyChannels(member);

    // ch3 (T+20), ch1 (T+10), ch2 (NULL → createdAt fallback, 가장 오래됨)
    assertThat(rows).extracting(ChatChannel::getName).containsExactly("ch3", "ch1", "ch2");
  }

  @Test
  @DisplayName("findByDmMemberMinAndDmMemberMax — pair 단일 lookup")
  void findByDmMemberMinAndDmMemberMax_works() {
    ChatChannel dm =
        save(
            ChatChannel.builder()
                .name("DM:2:5")
                .type(ChatChannelType.DM)
                .dmMemberMin(2L)
                .dmMemberMax(5L)
                .build(),
            null,
            null,
            null);
    em.flush();

    Optional<ChatChannel> hit = channelRepository.findByDmMemberMinAndDmMemberMax(2L, 5L);
    assertThat(hit).isPresent();
    assertThat(hit.get().getId()).isEqualTo(dm.getId());

    Optional<ChatChannel> miss = channelRepository.findByDmMemberMinAndDmMemberMax(99L, 100L);
    assertThat(miss).isEmpty();
  }

  @Test
  @DisplayName("updateLastMessage — strictly greater일 때만 갱신, stale은 0 row")
  void updateLastMessage_is_monotonic_guarded() {
    ChatChannel ch =
        save(
            ChatChannel.builder().name("ch").type(ChatChannelType.PUBLIC).build(),
            null,
            null,
            null);
    em.flush();
    Long id = ch.getId();
    LocalDateTime t1 = LocalDateTime.of(2026, 5, 8, 13, 0, 0);
    LocalDateTime t2 = t1.plusSeconds(1);

    // 첫 갱신 — NULL → t1, msgId=10. 1 row affected.
    int n1 = channelRepository.updateLastMessage(id, 10L, t1);
    assertThat(n1).isEqualTo(1);

    // newer 갱신 — t1 → t2. 1 row affected.
    int n2 = channelRepository.updateLastMessage(id, 11L, t2);
    assertThat(n2).isEqualTo(1);

    // stale (t1 다시 시도) — 0 row affected.
    int n3 = channelRepository.updateLastMessage(id, 9L, t1);
    assertThat(n3).isZero();

    // tie ts + smaller id — 0 row affected (stale).
    int n4 = channelRepository.updateLastMessage(id, 5L, t2);
    assertThat(n4).isZero();

    // tie ts + larger id — 1 row affected (정확한 tie-break).
    int n5 = channelRepository.updateLastMessage(id, 99L, t2);
    assertThat(n5).isEqualTo(1);

    em.clear();
    ChatChannel after = channelRepository.findById(id).orElseThrow();
    assertThat(after.getLastMessageId()).isEqualTo(99L);
    assertThat(after.getLastMessageAt()).isEqualTo(t2);
  }

  /** save + lastMessage 강제 박제(native UPDATE) + EM clear. */
  private ChatChannel save(ChatChannel ch, Long ignored, LocalDateTime lastAt, Long lastId) {
    ChatChannel saved = channelRepository.saveAndFlush(ch);
    if (lastAt != null) {
      jdbcTemplate.update(
          "UPDATE chat_channels SET last_message_at = ?, last_message_id = ? WHERE id = ?",
          lastAt,
          lastId,
          saved.getId());
      em.clear();
      saved = channelRepository.findById(saved.getId()).orElseThrow();
    }
    return saved;
  }
}

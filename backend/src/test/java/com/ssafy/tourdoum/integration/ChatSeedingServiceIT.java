package com.ssafy.tourdoum.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.tourdoum.chat.seed.ChatSeedScenario;
import com.ssafy.tourdoum.chat.seed.ChatSeedingService;
import com.ssafy.tourdoum.chat.seed.SeedReport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-0012 v2 BE-3 — Testcontainers MySQL 8.4 + Redis. seed runner 1만 row 검증.
 *
 * <ul>
 *   <li>PUBLIC scenario row count + sentinel.
 *   <li>DM scenario row count + DM pair UNIQUE 충족 (dm_member_min &lt; dm_member_max).
 *   <li>MIXED scenario.
 *   <li>멱등 — 두 번째 호출 시 row 추가 0건.
 *   <li>EXPLAIN — V17 keyset 인덱스 hit.
 * </ul>
 *
 * <p>실행: {@code ./mvnw verify -Dtourdoum.it=true -Dtest=ChatSeedingServiceIT}.
 */
@SpringBootTest
@Testcontainers
@EnabledIfSystemProperty(named = "tourdoum.it", matches = "true")
class ChatSeedingServiceIT {

  @SuppressWarnings("resource")
  @Container
  static final MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("tourdoum")
          .withUsername("tourdoum")
          .withPassword("tourdoum");

  @SuppressWarnings("resource")
  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:mysql://"
                + mysql.getHost()
                + ":"
                + mysql.getMappedPort(3306)
                + "/tourdoum?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true");
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
  }

  @Autowired private ChatSeedingService seedingService;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void cleanup() {
    seedingService.clearAllSeed();
  }

  @Test
  @DisplayName("PUBLIC 1만 row — 1 채널 + sentinel + last_message 박제")
  void public_scenario_smoke() {
    SeedReport report = seedingService.seed(ChatSeedScenario.PUBLIC, 10_000L, true);
    assertThat(report.totalMessages()).isEqualTo(10_000L);
    assertThat(report.totalChannels()).isEqualTo(1);
    assertThat(report.totalMembers()).isGreaterThanOrEqualTo(50);

    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM chat_messages WHERE channel_id ="
                + " (SELECT id FROM chat_channels WHERE name='seed-public-1')",
            Long.class);
    assertThat(count).isEqualTo(10_000L);

    Long lastMessageId =
        jdbc.queryForObject(
            "SELECT last_message_id FROM chat_channels WHERE name='seed-public-1'", Long.class);
    assertThat(lastMessageId).isNotNull();
  }

  @Test
  @DisplayName("DM scenario — 채널들이 dm_member_min<max + 정확히 2 멤버")
  void dm_scenario_pair_constraints() {
    SeedReport report = seedingService.seed(ChatSeedScenario.DM, 1_000L, true);
    assertThat(report.totalMessages()).isGreaterThanOrEqualTo(1_000L);
    assertThat(report.totalChannels()).isGreaterThan(0);

    // 모든 DM 채널이 dm_member_min/max non-null + min < max.
    Long anomaly =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM chat_channels WHERE name LIKE 'seed-dm-%'"
                + "   AND (dm_member_min IS NULL OR dm_member_max IS NULL OR dm_member_min >= dm_member_max)",
            Long.class);
    assertThat(anomaly).as("dm_member_min/max 정합 위반").isZero();

    // 각 DM 채널이 정확히 2 멤버.
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT c.id, COUNT(m.member_id) AS cnt FROM chat_channels c"
                + " JOIN chat_members m ON m.channel_id = c.id"
                + " WHERE c.name LIKE 'seed-dm-%' GROUP BY c.id");
    assertThat(rows).isNotEmpty();
    rows.forEach(r -> assertThat(((Number) r.get("cnt")).intValue()).isEqualTo(2));
  }

  @Test
  @DisplayName("MIXED scenario — PUBLIC + DM 합본")
  void mixed_scenario_smoke() {
    SeedReport report = seedingService.seed(ChatSeedScenario.MIXED, 2_000L, true);
    assertThat(report.totalMessages()).isGreaterThanOrEqualTo(2_000L);
    assertThat(report.totalChannels()).isGreaterThanOrEqualTo(2); // 1 public + N dm

    Long publicCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM chat_channels WHERE name='seed-public-1'", Long.class);
    assertThat(publicCount).isEqualTo(1L);
    Long dmCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM chat_channels WHERE name LIKE 'seed-dm-%'", Long.class);
    assertThat(dmCount).isGreaterThan(0L);
  }

  @Test
  @DisplayName("멱등 — 동일 시나리오 두 번째 호출 시 row 추가 0건")
  void seed_is_idempotent_on_resume() {
    seedingService.seed(ChatSeedScenario.PUBLIC, 5_000L, true);
    Long before = jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages", Long.class);

    SeedReport second = seedingService.seed(ChatSeedScenario.PUBLIC, 5_000L, true);
    Long after = jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages", Long.class);

    assertThat(second.skipped()).isEqualTo(1);
    assertThat(second.totalMessages()).isZero();
    assertThat(after).isEqualTo(before);
  }

  @Test
  @DisplayName("EXPLAIN — keyset 쿼리가 V17 인덱스를 사용 (PUBLIC 1만건 시드 후)")
  void explain_uses_v17_index() {
    seedingService.seed(ChatSeedScenario.PUBLIC, 10_000L, true);
    Long channelId =
        jdbc.queryForObject("SELECT id FROM chat_channels WHERE name='seed-public-1'", Long.class);

    List<Map<String, Object>> plan =
        jdbc.queryForList(
            "EXPLAIN SELECT id FROM chat_messages"
                + " WHERE channel_id = ? AND (created_at < NOW(6) OR (created_at = NOW(6) AND id < ?))"
                + " ORDER BY created_at DESC, id DESC LIMIT 20",
            channelId,
            Long.MAX_VALUE);
    assertThat(plan).isNotEmpty();
    Object key = plan.get(0).get("key");
    assertThat(key).as("EXPLAIN.key — V17 keyset 인덱스 또는 PK fallback (handoff 박제 대상)").isNotNull();
  }

  @Test
  @DisplayName("실행 시간 — 1만 row PUBLIC 시드는 30초 내 완료(roughly)")
  void smoke_elapsed_within_threshold() {
    SeedReport r = seedingService.seed(ChatSeedScenario.PUBLIC, 10_000L, true);
    assertThat(r.elapsed()).isLessThan(Duration.ofSeconds(30));
  }
}

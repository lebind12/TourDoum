package com.ssafy.tourdoum.chat.seed;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 seed 시나리오 실행기 — ADR-0012 v2 BE-3.
 *
 * <p>JdbcTemplate batch INSERT + Zipf-like skew + 90일 균등 분포. 멱등: 시나리오별 sentinel 채널 이름으로 이미 시드된 경우
 * skip.
 *
 * <p>production 절대 미실행 — caller {@link ChatSeedRunner}가 `chat-seed` profile gate. 본 service 자체는
 * profile 무관(IT에서 직접 호출 가능).
 */
@Service
public class ChatSeedingService {

  private static final Logger LOG = LoggerFactory.getLogger(ChatSeedingService.class);

  private static final String PUBLIC_SENTINEL = "seed-public-1";
  private static final String DM_SENTINEL_PREFIX = "seed-dm-";
  private static final String SEED_USER_PREFIX = "seed-user-";

  /** PUBLIC 시나리오 default — 1 채널 + 1000 멤버 + 2,500,000 메시지 (ADR §Seed). */
  public static final long PUBLIC_TARGET_MESSAGES = 2_500_000L;

  public static final int PUBLIC_TARGET_MEMBERS = 1_000;

  /** DM 시나리오 default — 500 채널 × 5000 메시지 = 2,500,000 (ADR §Seed). */
  public static final int DM_TARGET_CHANNELS = 500;

  public static final int DM_MESSAGES_PER_CHANNEL = 5_000;

  /** 메시지 분산 기간(seed-now 기준 과거 90일). */
  private static final Duration TIME_WINDOW = Duration.ofDays(90);

  /** JDBC batch chunk — rewriteBatchedStatements=true 가정. */
  private static final int BATCH = 1_000;

  private final JdbcTemplate jdbc;

  public ChatSeedingService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * 시나리오 실행. {@code totalRows}는 ADR default를 override해 IT/소규모 검증용. null이면 default 사용.
   *
   * @param scenario 시나리오
   * @param totalRowsOrNull 메시지 총량 override (null = default per scenario)
   * @param resume true면 sentinel hit 시 skip(멱등). false면 sentinel 무시하고 추가 박제.
   */
  @Transactional(propagation = Propagation.NEVER)
  public SeedReport seed(ChatSeedScenario scenario, Long totalRowsOrNull, boolean resume) {
    Instant start = Instant.now();
    long messages = 0;
    int channels = 0;
    int newMembers = 0;
    int skipped = 0;

    LOG.info("[seed] scenario={} totalRows={} resume={}", scenario, totalRowsOrNull, resume);

    switch (scenario) {
      case PUBLIC -> {
        long target = totalRowsOrNull != null ? totalRowsOrNull : PUBLIC_TARGET_MESSAGES;
        var r = seedPublic(target, resume);
        messages += r.messages;
        channels += r.channels;
        newMembers += r.members;
        skipped += r.skipped;
      }
      case DM -> {
        long target =
            totalRowsOrNull != null
                ? totalRowsOrNull
                : DM_TARGET_CHANNELS * (long) DM_MESSAGES_PER_CHANNEL;
        var r = seedDm(target, resume);
        messages += r.messages;
        channels += r.channels;
        newMembers += r.members;
        skipped += r.skipped;
      }
      case MIXED -> {
        long target =
            totalRowsOrNull != null
                ? totalRowsOrNull
                : (PUBLIC_TARGET_MESSAGES + DM_TARGET_CHANNELS * (long) DM_MESSAGES_PER_CHANNEL);
        long perScenario = target / 2;
        var p = seedPublic(perScenario, resume);
        var d = seedDm(target - perScenario, resume);
        messages += p.messages + d.messages;
        channels += p.channels + d.channels;
        newMembers += p.members + d.members;
        skipped += p.skipped + d.skipped;
      }
    }
    Duration elapsed = Duration.between(start, Instant.now());
    LOG.info(
        "[seed] DONE scenario={} messages={} channels={} newMembers={} skipped={} elapsed={}s",
        scenario,
        messages,
        channels,
        newMembers,
        skipped,
        elapsed.toSeconds());
    return new SeedReport(scenario, messages, channels, newMembers, skipped, elapsed);
  }

  private RunCounts seedPublic(long targetMessages, boolean resume) {
    Long existing = findChannelIdByName(PUBLIC_SENTINEL);
    if (existing != null && resume) {
      LOG.info("[seed] PUBLIC sentinel '{}' present (id={}), skip.", PUBLIC_SENTINEL, existing);
      return new RunCounts(0, 0, 0, 1);
    }
    int memberCount = Math.min(PUBLIC_TARGET_MEMBERS, Math.max(50, (int) (targetMessages / 1_000)));
    List<Long> memberIds = ensureSeedMembers(memberCount);
    long channelId = createChannel(PUBLIC_SENTINEL, "PUBLIC", null, null);
    insertChatMembers(channelId, memberIds);
    long inserted = insertMessagesZipf(channelId, memberIds, targetMessages);
    updateChannelLastMessage(channelId);
    return new RunCounts(inserted, 1, memberIds.size(), 0);
  }

  private RunCounts seedDm(long targetMessages, boolean resume) {
    int channelTarget = (int) Math.max(1, Math.min(DM_TARGET_CHANNELS, targetMessages / 100));
    int messagesPerChannel = (int) Math.max(1, targetMessages / channelTarget);
    // 멤버 풀: 채널 수 × 2 unique pair를 만들 수 있을 만큼.
    // nC2 >= channelTarget → n >= ceil((1+sqrt(1+8C))/2).
    int memberPool = Math.max(33, (int) Math.ceil((1 + Math.sqrt(1 + 8.0 * channelTarget)) / 2));
    List<Long> members = ensureSeedMembers(memberPool);

    int channelsCreated = 0;
    long messagesInserted = 0;
    Random rand = new Random(0xC0FFEEL);
    int dmIdx = 0;
    outer:
    for (int i = 0; i < members.size(); i++) {
      for (int j = i + 1; j < members.size(); j++) {
        if (channelsCreated >= channelTarget) {
          break outer;
        }
        dmIdx++;
        String name = DM_SENTINEL_PREFIX + members.get(i) + "-" + members.get(j);
        Long existing = findChannelIdByName(name);
        if (existing != null && resume) {
          // 이미 시드됨 — skip.
          continue;
        }
        long min = members.get(i);
        long max = members.get(j);
        long channelId = createChannel(name, "DM", min, max);
        insertChatMembers(channelId, List.of(min, max));
        long inserted =
            insertMessagesUniform(channelId, List.of(min, max), messagesPerChannel, rand);
        updateChannelLastMessage(channelId);
        channelsCreated++;
        messagesInserted += inserted;
      }
    }
    int skipped = (channelsCreated == 0 && resume) ? 1 : 0;
    return new RunCounts(messagesInserted, channelsCreated, members.size(), skipped);
  }

  /** 시드용 회원 풀 생성/회수 — `seed-user-{i}@example.com`. */
  List<Long> ensureSeedMembers(int n) {
    List<Long> ids = new ArrayList<>(n);
    for (int i = 1; i <= n; i++) {
      String email = SEED_USER_PREFIX + i + "@example.com";
      Long existing =
          jdbc.query(
              "SELECT id FROM members WHERE email = ?",
              ps -> ps.setString(1, email),
              rs -> rs.next() ? rs.getLong(1) : null);
      if (existing != null) {
        ids.add(existing);
        continue;
      }
      jdbc.update(
          "INSERT INTO members (email, password, nickname, role, created_at, updated_at)"
              + " VALUES (?, ?, ?, 'ROLE_USER', NOW(6), NOW(6))",
          email,
          "$2a$10$seed.placeholder.bcrypt.hash.value.000000000000000000000",
          SEED_USER_PREFIX + i);
      Long id =
          jdbc.queryForObject(
              "SELECT id FROM members WHERE email = ?", new Object[] {email}, Long.class);
      ids.add(id);
    }
    return ids;
  }

  private Long findChannelIdByName(String name) {
    return jdbc.query(
        "SELECT id FROM chat_channels WHERE name = ?",
        ps -> ps.setString(1, name),
        rs -> rs.next() ? rs.getLong(1) : null);
  }

  private long createChannel(String name, String type, Long dmMin, Long dmMax) {
    jdbc.update(
        "INSERT INTO chat_channels (name, type, dm_member_min, dm_member_max, created_at)"
            + " VALUES (?, ?, ?, ?, NOW(6))",
        name,
        type,
        dmMin,
        dmMax);
    Long id =
        jdbc.queryForObject(
            "SELECT id FROM chat_channels WHERE name = ?", new Object[] {name}, Long.class);
    if (id == null) {
      throw new IllegalStateException("created channel disappeared: " + name);
    }
    return id;
  }

  private void insertChatMembers(long channelId, List<Long> memberIds) {
    jdbc.batchUpdate(
        "INSERT IGNORE INTO chat_members (channel_id, member_id, joined_at) VALUES (?, ?, NOW(6))",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
            ps.setLong(1, channelId);
            ps.setLong(2, memberIds.get(i));
          }

          @Override
          public int getBatchSize() {
            return memberIds.size();
          }
        });
  }

  /** PUBLIC 시나리오 — 시간 균등 + Zipf-like sender skew (rank=1이 ~30%, rank N은 ~0.5%). */
  private long insertMessagesZipf(long channelId, List<Long> memberIds, long total) {
    Instant end = Instant.now();
    Instant beginInstant = end.minus(TIME_WINDOW);
    long beginMs = beginInstant.toEpochMilli();
    long endMs = end.toEpochMilli();

    // Zipf weights via rank-based 1/rank.
    double[] cumulative = new double[memberIds.size()];
    double total_weight = 0;
    for (int i = 0; i < memberIds.size(); i++) {
      double w = 1.0 / (i + 1.0);
      total_weight += w;
      cumulative[i] = total_weight;
    }
    final double sumWeight = total_weight;
    Random rand = new Random(0xDEADBEEFL ^ channelId);

    return batchInsert(
        channelId,
        total,
        BATCH,
        idx -> {
          long ms = beginMs + (long) (rand.nextDouble() * (endMs - beginMs));
          // Zipf pick by cumulative search.
          double r = rand.nextDouble() * sumWeight;
          int pick = 0;
          for (int i = 0; i < cumulative.length; i++) {
            if (r <= cumulative[i]) {
              pick = i;
              break;
            }
          }
          return new MessageRow(memberIds.get(pick), ms);
        });
  }

  /** DM 시나리오 — 두 멤버가 각 ~50%로 송신. 시간 균등. */
  private long insertMessagesUniform(
      long channelId, List<Long> memberIds, int total, Random sharedRand) {
    Instant end = Instant.now();
    long beginMs = end.minus(TIME_WINDOW).toEpochMilli();
    long endMs = end.toEpochMilli();
    Random rand = new Random(sharedRand.nextLong());
    return batchInsert(
        channelId,
        total,
        BATCH,
        idx -> {
          long ms = beginMs + (long) (rand.nextDouble() * (endMs - beginMs));
          long sender = memberIds.get(rand.nextInt(memberIds.size()));
          return new MessageRow(sender, ms);
        });
  }

  /** 공통 batch INSERT. {@code rowSupplier}가 sender + epochMs 반환. */
  private long batchInsert(long channelId, long total, int batchSize, RowSupplier rowSupplier) {
    long inserted = 0;
    long remaining = total;
    long globalIdx = 0;
    while (remaining > 0) {
      int chunk = (int) Math.min(remaining, batchSize);
      final long startIdx = globalIdx;
      final int finalChunk = chunk;
      jdbc.batchUpdate(
          "INSERT INTO chat_messages (channel_id, sender_id, content, created_at)"
              + " VALUES (?, ?, ?, ?)",
          new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
              MessageRow row = rowSupplier.next(startIdx + i);
              ps.setLong(1, channelId);
              ps.setLong(2, row.senderId());
              ps.setString(3, "seed-msg-" + (startIdx + i));
              ps.setTimestamp(4, new Timestamp(row.epochMs()));
            }

            @Override
            public int getBatchSize() {
              return finalChunk;
            }
          });
      inserted += chunk;
      remaining -= chunk;
      globalIdx += chunk;
      if (inserted % (batchSize * 50) == 0) {
        LOG.info("[seed] channel={} inserted={}/{}", channelId, inserted, total);
      }
    }
    return inserted;
  }

  /** 시드 끝난 후 last_message 박제 — guarded UPDATE로 ROW_NUMBER 1번 행 박제. */
  private void updateChannelLastMessage(long channelId) {
    jdbc.update(
        "UPDATE chat_channels c"
            + " JOIN ("
            + "   SELECT channel_id, id, created_at,"
            + "          ROW_NUMBER() OVER (PARTITION BY channel_id ORDER BY created_at DESC, id DESC) AS rn"
            + "   FROM chat_messages"
            + "   WHERE channel_id = ?"
            + " ) m ON m.channel_id = c.id AND m.rn = 1"
            + " SET c.last_message_id = m.id, c.last_message_at = m.created_at"
            + " WHERE c.id = ?",
        channelId,
        channelId);
  }

  @FunctionalInterface
  private interface RowSupplier {
    MessageRow next(long globalIdx);
  }

  private record MessageRow(long senderId, long epochMs) {}

  private record RunCounts(long messages, int channels, int members, int skipped) {}

  /** 학습용 helper — handoff에 박제할 LocalDateTime now epoch. */
  @SuppressWarnings("unused")
  private static LocalDateTime nowUtc() {
    return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
  }

  /** 시드 데이터 정리(IT용). production 미사용. */
  public void clearAllSeed() {
    jdbc.update(
        "DELETE FROM chat_messages WHERE channel_id IN"
            + " (SELECT id FROM chat_channels WHERE name LIKE 'seed-%')");
    jdbc.update(
        "DELETE FROM chat_members WHERE channel_id IN"
            + " (SELECT id FROM chat_channels WHERE name LIKE 'seed-%')");
    jdbc.update("DELETE FROM chat_channels WHERE name LIKE 'seed-%'");
    jdbc.update("DELETE FROM members WHERE email LIKE 'seed-user-%@example.com'");
  }

  // ThreadLocalRandom 보존(Future 사용 가능).
  @SuppressWarnings("unused")
  private static long randomMs(long beginMs, long endMs) {
    return beginMs + ThreadLocalRandom.current().nextLong(endMs - beginMs);
  }
}

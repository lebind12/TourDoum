-- V17: 채팅 keyset paging 인덱스 + DM pair UNIQUE + last_message denormalize
-- ADR-0012 v2 §V17 — 4 단계 (chat_messages keyset / chat_members 역방향 / DM pair / last_message)
--
-- 절차 (production 적용 시):
--   1) 컬럼 추가 (NULL 허용 — 기존 row를 깨지 않는다)
--   2) backfill (DM pair는 멤버 정확히 2명 채널만 / last_message는 ROW_NUMBER 최신 1건)
--   3) anomaly 검증 (DM 멤버 ≠ 2 / pair 중복 / NULL 정합성) — 본 마이그레이션은 dev/test 데이터 가정
--   4) UNIQUE INDEX + CHECK constraint
--
-- 학습 단계 dev/test에선 chat_channels 시드 데이터가 없거나 정합 보장된 상태라 (3) 검증을 SQL로 자동
-- 실패시키지 않는다. production 적용 절차는 handoff.md §V17 절차 표 참조.

-- ─────────────────────────────────────────────────────────────────────────
-- 1) chat_messages keyset 인덱스 — (channel_id, created_at DESC, id DESC)
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE chat_messages
  ADD INDEX idx_chat_messages_channel_created_id (channel_id, created_at DESC, id DESC);

-- ─────────────────────────────────────────────────────────────────────────
-- 2) chat_members 역방향 인덱스 — PK가 (channel_id, member_id)이라 member_id 단독은 leftmost 위반.
--    findMyChannels / unread / mute 등 future lookup에 모두 필요 (P0).
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE chat_members
  ADD INDEX idx_chat_members_member (member_id, channel_id);

-- ─────────────────────────────────────────────────────────────────────────
-- 3) chat_channels DM pair — 컬럼 + backfill + UNIQUE + CHECK
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE chat_channels
  ADD COLUMN dm_member_min BIGINT NULL AFTER type,
  ADD COLUMN dm_member_max BIGINT NULL AFTER dm_member_min;

-- backfill: type='DM' + 멤버 정확히 2명인 채널만 (멤버 ≠ 2인 비정상 채널은 NULL 유지 → 후속 정리)
UPDATE chat_channels c
JOIN (
  SELECT channel_id,
         MIN(member_id) AS m_min,
         MAX(member_id) AS m_max
  FROM chat_members
  GROUP BY channel_id
  HAVING COUNT(*) = 2 AND MIN(member_id) <> MAX(member_id)
) m ON m.channel_id = c.id
SET c.dm_member_min = m.m_min,
    c.dm_member_max = m.m_max
WHERE c.type = 'DM';

-- UNIQUE INDEX (NULL은 MySQL UNIQUE에서 중복 허용이라 PUBLIC은 영향 없음).
ALTER TABLE chat_channels
  ADD CONSTRAINT uk_chat_channels_dm_pair UNIQUE (dm_member_min, dm_member_max);

-- CHECK (MySQL 8.0.16+) — 두 컬럼 정합 + min < max 강제 + PUBLIC NULL 강제.
ALTER TABLE chat_channels
  ADD CONSTRAINT chk_chat_channels_dm_pair CHECK (
    (type = 'DM'
       AND dm_member_min IS NOT NULL
       AND dm_member_max IS NOT NULL
       AND dm_member_min < dm_member_max)
    OR
    (type <> 'DM'
       AND dm_member_min IS NULL
       AND dm_member_max IS NULL)
  );

-- ─────────────────────────────────────────────────────────────────────────
-- 4) chat_channels last_message denormalize — 컬럼 + backfill + 인덱스
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE chat_channels
  ADD COLUMN last_message_id BIGINT      NULL,
  ADD COLUMN last_message_at DATETIME(6) NULL;

-- backfill: 각 채널의 ROW_NUMBER OVER (created_at DESC, id DESC) = 1 행
UPDATE chat_channels c
JOIN (
  SELECT channel_id, id, created_at,
         ROW_NUMBER() OVER (
           PARTITION BY channel_id
           ORDER BY created_at DESC, id DESC
         ) AS rn
  FROM chat_messages
) m ON m.channel_id = c.id AND m.rn = 1
SET c.last_message_id = m.id,
    c.last_message_at = m.created_at;

-- 채널 목록 정렬용 인덱스 — last_message_at DESC + id DESC (tie-break)
ALTER TABLE chat_channels
  ADD INDEX idx_chat_channels_last_message_at (last_message_at DESC, id DESC);

-- V18: ADR-0013 BE-13 — Reservation FSM + transition_log + outbox 테이블 (publisher 미포함, BE-15 별 task)
-- Hybrid 5요소: state column SOT + append-only audit log + transactional outbox + idempotency_key + (PG webhook 대사는 학습 단계 mock)

-- 1) reservations.state — FSM SOT. 기존 status(PENDING/CONFIRMED/CANCELED)와 병존 (BE-14 통합 예정).
ALTER TABLE reservations
    ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED'
        COMMENT 'FSM SOT — QUEUED/ADMITTED/INVENTORY_RESERVED/PAYMENT_PENDING/AUTHORIZED/CAPTURED/CONFIRMED/REJECTED/CANCELLED/REVERSED/REFUND_PENDING/REFUNDED/FAILED',
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD INDEX idx_reservations_state (state);

-- 2) reservation_transition_log — append-only audit (전자상거래법 시행령 §6 — 5년 보존).
CREATE TABLE reservation_transition_log
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT       NOT NULL,
    from_state     VARCHAR(32)  NULL COMMENT '최초 INSERT 시 NULL',
    to_state       VARCHAR(32)  NOT NULL,
    metadata       JSON         NULL,
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_rtl_reservation (reservation_id, created_at),
    INDEX idx_rtl_to_state (to_state, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = 'FSM transition append-only audit (5년 보존)';

-- 3) outbox — transactional outbox + claim_state 기반 동시성 제어 (BE-15 publisher).
CREATE TABLE outbox
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_id  BIGINT       NOT NULL COMMENT 'reservation.id 등',
    event_type    VARCHAR(64)  NOT NULL COMMENT 'PaymentRequested/InventoryRelease/Notify/RefundScheduled',
    payload       JSON         NULL,
    claim_state   VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLAIMED/DONE/FAILED',
    claimed_by    VARCHAR(64)  NULL COMMENT 'replica id',
    claimed_at    TIMESTAMP(6) NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    available_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '지연 발행 due (RefundScheduled 등)',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at  TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_claim_state_available (claim_state, available_at, id),
    INDEX idx_outbox_aggregate (aggregate_id, event_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = 'Transactional outbox — BE-15 publisher worker가 claim/publish';

-- 4) outbox_dead_letter — poison event 격리. Admin UI에서 수동 재시도.
CREATE TABLE outbox_dead_letter
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    original_id     BIGINT       NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload         JSON         NULL,
    failure_reason  TEXT         NULL,
    attempt_count   INT          NOT NULL,
    moved_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_outbox_dlq_aggregate (aggregate_id, event_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = 'Outbox poison event 격리 — Admin UI 재시도 대상';

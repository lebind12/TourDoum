-- V10: 예약 테이블
-- idempotency_key UNIQUE: 동일 키로 중복 confirm 방지 (Redis 없이 DB 레벨 멱등성)
CREATE TABLE reservations
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    member_id        BIGINT       NOT NULL,
    accommodation_id BIGINT       NOT NULL,
    check_in         DATE         NOT NULL,
    check_out        DATE         NOT NULL,
    guests           INT          NOT NULL,
    total_price      INT          NOT NULL,
    payment_method   VARCHAR(20)  NOT NULL COMMENT 'CARD | KAKAOPAY | TOSS',
    status           VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED' COMMENT 'PENDING | CONFIRMED | CANCELED',
    idempotency_key  VARCHAR(128) NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservations_idempotency_key (idempotency_key),
    INDEX idx_reservations_member_id (member_id),
    INDEX idx_reservations_accommodation_id (accommodation_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- V12: 알림 테이블 (폴링 기반, WebSocket/SSE 미사용)
-- read_at NULL = 미읽음, NOT NULL = 읽음
CREATE TABLE notifications
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL COMMENT 'REVIEW_REPLY | RESERVATION_CONFIRMED | RESERVATION_CANCELED | SYSTEM',
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(500) NOT NULL,
    link_url   VARCHAR(500),
    read_at    TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_notifications_member_id (member_id),
    INDEX idx_notifications_member_unread (member_id, read_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

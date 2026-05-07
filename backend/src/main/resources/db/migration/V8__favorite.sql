-- V8: 즐겨찾기 테이블
-- UNIQUE(member_id, target_type, target_id): 동일 대상 중복 즐겨찾기 방지 (DB 레벨)
CREATE TABLE favorites
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   BIGINT      NOT NULL,
    target_type VARCHAR(20) NOT NULL COMMENT 'ATTRACTION | ACCOMMODATION',
    target_id   BIGINT      NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorites_member_target (member_id, target_type, target_id),
    INDEX idx_favorites_member_id (member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

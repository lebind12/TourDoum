-- V9: 후기 테이블
CREATE TABLE reviews
(
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    member_id   BIGINT        NOT NULL,
    target_type VARCHAR(20)   NOT NULL COMMENT 'ATTRACTION | ACCOMMODATION',
    target_id   BIGINT        NOT NULL,
    rating      TINYINT       NOT NULL COMMENT '1~5',
    title       VARCHAR(200),
    content     TEXT          NOT NULL,
    created_at  TIMESTAMP(6)  NOT NULL,
    updated_at  TIMESTAMP(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_reviews_target (target_type, target_id),
    INDEX idx_reviews_member_id (member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

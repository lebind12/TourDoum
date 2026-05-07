-- V11: 여행 계획 테이블
CREATE TABLE plans
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    title      VARCHAR(200) NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_plans_member_id (member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- V11: 여행 계획 아이템 테이블
CREATE TABLE plan_items
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    plan_id      BIGINT      NOT NULL,
    day_index    INT         NOT NULL,
    order_index  INT         NOT NULL,
    target_type  VARCHAR(20) NOT NULL COMMENT 'ATTRACTION | ACCOMMODATION',
    target_id    BIGINT      NOT NULL,
    memo         VARCHAR(500),
    PRIMARY KEY (id),
    INDEX idx_plan_items_plan_id (plan_id),
    CONSTRAINT fk_plan_items_plan FOREIGN KEY (plan_id) REFERENCES plans (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

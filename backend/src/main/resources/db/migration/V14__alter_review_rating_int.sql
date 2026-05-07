-- V14: reviews.rating TINYINT → INT
-- Hibernate 6.5+ JdbcType 매칭 엄격화로 schema-validation에서 TINYINT vs Integer 충돌 발생.
-- ADR-0004 (Flyway가 schema 단일 책임) 일관성 → migration으로 컬럼 타입 정렬.
ALTER TABLE reviews
    MODIFY rating INT NOT NULL COMMENT '1~5 (Hibernate int)';

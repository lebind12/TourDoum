-- V1__init.sql
-- TourDoum baseline 마이그레이션 — develop 분기 기준 초기 스키마
-- 생성일: 2026-05-06 | 참고: docs/adr/0004-db-migration.md
--
-- 주의: 이 파일은 수정하지 않는다. 체크섬이 깨지면 Flyway 기동 실패.
-- 변경 필요 시 V2__xxx.sql 등 새 번호로 작성 후 보상 마이그레이션.
--
-- 정합 기준: com.ssafy.tourdoum.member.Member (ADR-0003)
-- @Table(name = "members")
-- @Id BIGINT IDENTITY
-- email VARCHAR(255) NOT NULL UNIQUE
-- password VARCHAR(255) NOT NULL
-- nickname VARCHAR(50) NOT NULL UNIQUE
-- role VARCHAR(20) NOT NULL  (@Enumerated(STRING) — MemberRole)
-- created_at TIMESTAMP(6) NOT NULL  (@CreatedDate, updatable=false)
-- updated_at TIMESTAMP(6) NOT NULL  (@LastModifiedDate)

CREATE TABLE members
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    nickname   VARCHAR(50)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_email (email),
    UNIQUE KEY uk_members_nickname (nickname)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

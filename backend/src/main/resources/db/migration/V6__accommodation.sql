-- ADR-0002 패턴: 숙박 테이블 생성
-- location 컬럼은 GENERATED STORED POINT(lng, lat) SRID 4326 가상 컬럼.
-- JPA는 lat/lng DECIMAL만 다루고 location은 DB가 자동 유지한다.
-- SPATIAL INDEX로 ST_Distance_Sphere 반경 검색 성능 확보.

CREATE TABLE accommodations
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    name          VARCHAR(255)  NOT NULL,
    type          VARCHAR(20)   NOT NULL COMMENT 'HOTEL|PENSION|GUESTHOUSE|MOTEL',
    address       VARCHAR(500),
    lat           DECIMAL(9, 6) NOT NULL,
    lng           DECIMAL(9, 6) NOT NULL,
    price_from    INT           COMMENT '1박 최저가 (원)',
    rating        DECIMAL(3, 1) COMMENT '평균 별점 0.0~5.0',
    thumbnail_url VARCHAR(1000),
    description   TEXT,
    -- GENERATED STORED 가상 컬럼: JPA 매핑 불필요, DB가 자동 계산 (Attraction 동일 패턴)
    location      POINT GENERATED ALWAYS AS (ST_SRID(POINT(lng, lat), 4326)) STORED NOT NULL SRID 4326,
    created_at    TIMESTAMP(6)  NOT NULL,
    updated_at    TIMESTAMP(6)  NOT NULL,

    PRIMARY KEY (id),
    SPATIAL INDEX idx_accommodations_location (location),
    INDEX idx_accommodations_type (type),
    INDEX idx_accommodations_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- ADR-0002: 여행지 테이블 생성
-- location 컬럼은 GENERATED STORED POINT(lng, lat) SRID 4326 가상 컬럼.
-- JPA는 latitude/longitude DECIMAL만 다루고 location은 DB가 자동 유지한다.
-- SPATIAL INDEX로 ST_Distance_Sphere 반경 검색 성능 확보.

CREATE TABLE attractions
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    name       VARCHAR(255)  NOT NULL,
    region     VARCHAR(50)   NOT NULL,
    category   VARCHAR(20)   NOT NULL,
    address    VARCHAR(500),
    latitude   DECIMAL(9, 6) NOT NULL,
    longitude  DECIMAL(9, 6) NOT NULL,
    description TEXT,
    image_url  VARCHAR(1000),
    tour_api_id VARCHAR(50),
    -- GENERATED STORED 가상 컬럼: JPA 매핑 불필요, DB가 자동 계산
    location   POINT GENERATED ALWAYS AS (ST_SRID(POINT(longitude, latitude), 4326)) STORED NOT NULL SRID 4326,
    created_at TIMESTAMP(6)  NOT NULL,
    updated_at TIMESTAMP(6)  NOT NULL,

    PRIMARY KEY (id),
    SPATIAL INDEX idx_attractions_location (location),
    INDEX idx_attractions_region (region),
    INDEX idx_attractions_category (category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

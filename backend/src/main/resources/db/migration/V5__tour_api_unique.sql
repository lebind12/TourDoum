-- ADR-0005: tour_api_id UNIQUE 제약 추가
-- NULL 다중 허용 → V3 시드 50건(tour_api_id IS NULL)에 영향 없음.
-- ETL(V4)에서 삽입되는 레코드는 tour_api_id가 모두 NOT NULL이므로 중복 방지됨.

ALTER TABLE attractions
    ADD UNIQUE KEY uk_attractions_tour_api_id (tour_api_id);

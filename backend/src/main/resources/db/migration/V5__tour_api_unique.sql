-- attractions.tour_api_id에 UNIQUE 제약 — TourAPI 데이터 갱신 시 멱등성 보장.
-- V3 시드(tour_api_id NULL 50건)는 NULL 다중 허용으로 영향 없음.
ALTER TABLE attractions
  ADD UNIQUE KEY uk_attractions_tour_api_id (tour_api_id);

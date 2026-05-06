-- V4__tour_api_attractions.sql
-- 출처: 한국관광공사 TourAPI 4.0 (KorService2) [ADR-0005]
-- 생성방법: scripts/etl-tour-api.sh
--
-- ⚠ 이 파일은 TOUR_API_KEY 인증 실패(HTTP 401)로 인해 mock 데이터 5건만 포함한다.
-- 증상: API 서버가 HTTP 401 "Unauthorized" 를 반환 (JSON 응답 없음).
-- 원인 추정: data.go.kr 활용신청 미승인 또는 해당 API 미등록.
--
-- 실제 데이터 수집 절차:
--   1. https://www.data.go.kr/data/15101578/openapi.do 접속
--   2. 활용신청 → 서비스명: 한국관광공사_국문 관광정보 서비스_GW
--   3. 승인(보통 1~2 영업일) 후 인코딩 키(encoding key) 복사
--   4. .env 에 TOUR_API_KEY=<인코딩 키> 저장
--   5. scripts/etl-tour-api.sh 실행 → 이 파일이 실제 데이터로 교체됨

INSERT INTO attractions (name, region, category, address, latitude, longitude, image_url, tour_api_id, created_at, updated_at)
VALUES ('경복궁', '서울', 'HISTORY', '서울특별시 종로구 사직로 161', 37.579617, 126.976898, 'https://tong.visitkorea.or.kr/cms/resource/48/2672448_image2_1.jpg', 'MOCK-126559', NOW(6), NOW(6));

INSERT INTO attractions (name, region, category, address, latitude, longitude, image_url, tour_api_id, created_at, updated_at)
VALUES ('해운대해수욕장', '부산', 'NATURE', '부산광역시 해운대구 해운대해변로 264', 35.158701, 129.160359, 'https://tong.visitkorea.or.kr/cms/resource/23/2650123_image2_1.jpg', 'MOCK-126273', NOW(6), NOW(6));

INSERT INTO attractions (name, region, category, address, latitude, longitude, image_url, tour_api_id, created_at, updated_at)
VALUES ('성산일출봉', '제주', 'NATURE', '제주특별자치도 서귀포시 성산읍 성산리 1', 33.458484, 126.942634, 'https://tong.visitkorea.or.kr/cms/resource/93/2613393_image2_1.jpg', 'MOCK-126569', NOW(6), NOW(6));

INSERT INTO attractions (name, region, category, address, latitude, longitude, image_url, tour_api_id, created_at, updated_at)
VALUES ('불국사', '경북', 'HISTORY', '경상북도 경주시 불국로 385', 35.789637, 129.331670, 'https://tong.visitkorea.or.kr/cms/resource/75/2664375_image2_1.jpg', 'MOCK-126240', NOW(6), NOW(6));

INSERT INTO attractions (name, region, category, address, latitude, longitude, image_url, tour_api_id, created_at, updated_at)
VALUES ('남이섬', '강원', 'NATURE', '강원특별자치도 춘천시 남산면 남이섬길 1', 37.790300, 127.525900, 'https://tong.visitkorea.or.kr/cms/resource/54/2665554_image2_1.jpg', 'MOCK-126508', NOW(6), NOW(6));

-- 통계(mock): INSERT=5 / API 401로 실제 수집 불가
-- scripts/etl-tour-api.sh 재실행 시 실제 데이터로 교체됨

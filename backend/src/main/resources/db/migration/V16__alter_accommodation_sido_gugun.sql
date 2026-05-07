-- V16: Accommodation에 sido/gugun 행정구역 컬럼 추가 + V7 36행 시드 백필.
-- FE 필터(시도/구·군) 활성을 위한 BE 데이터 노출. 이전 #48에서 detail-only 분리한 결정과 별개로
-- 본 V16은 list/nearby/detail 모두에 sido/gugun을 노출한다(dispatch 명시).
--
-- 시드 백필 전략: 명시적 UPDATE 24개 (sido,gugun) 그룹화. SUBSTRING_INDEX 등 함수 의존 회피로
-- H2(MODE=MYSQL) 슬라이스 테스트와 MySQL 8 운영 환경 모두에서 결정적 동작 보장.
-- 매핑 근거: V7 시드 row의 address 첫 두 토큰 = sido / gugun.

ALTER TABLE accommodations
    ADD COLUMN sido  VARCHAR(20) NOT NULL DEFAULT '' AFTER address,
    ADD COLUMN gugun VARCHAR(40) NOT NULL DEFAULT '' AFTER sido;

-- ── 서울특별시 (12행, 9개 구) ─────────────────────────────────────────────
UPDATE accommodations SET sido='서울특별시', gugun='중구'    WHERE name IN ('명동 호텔', '서울역 호텔');
UPDATE accommodations SET sido='서울특별시', gugun='종로구'  WHERE name IN ('인사동 게스트하우스', '북촌 한옥 게스트하우스', '광화문 호텔');
UPDATE accommodations SET sido='서울특별시', gugun='강남구'  WHERE name = '강남 호텔';
UPDATE accommodations SET sido='서울특별시', gugun='마포구'  WHERE name = '홍대 모텔';
UPDATE accommodations SET sido='서울특별시', gugun='용산구'  WHERE name = '이태원 게스트하우스';
UPDATE accommodations SET sido='서울특별시', gugun='송파구'  WHERE name = '잠실 호텔';
UPDATE accommodations SET sido='서울특별시', gugun='서대문구' WHERE name = '신촌 모텔';
UPDATE accommodations SET sido='서울특별시', gugun='동대문구' WHERE name = '동대문 펜션';
UPDATE accommodations SET sido='서울특별시', gugun='광진구'  WHERE name = '건대 게스트하우스';

-- ── 부산광역시 (10행, 7개 구·군) ──────────────────────────────────────────
UPDATE accommodations SET sido='부산광역시', gugun='해운대구' WHERE name IN ('해운대 씨뷰 호텔', '해운대 모텔', '송정 서핑 게스트하우스');
UPDATE accommodations SET sido='부산광역시', gugun='수영구'   WHERE name = '광안리 펜션';
UPDATE accommodations SET sido='부산광역시', gugun='부산진구' WHERE name = '서면 비즈니스 호텔';
UPDATE accommodations SET sido='부산광역시', gugun='중구'     WHERE name = '남포동 게스트하우스';
UPDATE accommodations SET sido='부산광역시', gugun='기장군'   WHERE name IN ('기장 펜션', '오시리아 호텔');
UPDATE accommodations SET sido='부산광역시', gugun='동구'     WHERE name = '부산역 호텔';
UPDATE accommodations SET sido='부산광역시', gugun='영도구'   WHERE name = '영도 펜션';

-- ── 제주특별자치도 (8행, 2개 시) ──────────────────────────────────────────
UPDATE accommodations SET sido='제주특별자치도', gugun='서귀포시' WHERE name IN ('제주 중문 리조트', '성산 펜션', '서귀포 호텔');
UPDATE accommodations SET sido='제주특별자치도', gugun='제주시'   WHERE name IN ('제주시 게스트하우스', '협재 비치 펜션', '한라산 근처 펜션', '제주 모텔', '비자림 펜션');

-- ── 강원도/전라북도/경상북도/전라남도 (6행) ───────────────────────────────
UPDATE accommodations SET sido='강원도',     gugun='강릉시' WHERE name = '강릉 해변 펜션';
UPDATE accommodations SET sido='강원도',     gugun='속초시' WHERE name = '설악산 게스트하우스';
UPDATE accommodations SET sido='전라북도',   gugun='전주시' WHERE name = '전주 한옥마을 게스트하우스';
UPDATE accommodations SET sido='경상북도',   gugun='경주시' WHERE name = '경주 황리단길 모텔';
UPDATE accommodations SET sido='전라남도',   gugun='순천시' WHERE name = '순천만 펜션';
UPDATE accommodations SET sido='전라남도',   gugun='여수시' WHERE name = '여수 엑스포 호텔';

-- (sido, gugun) 페어 인덱스 — 향후 list 필터 쿼리(`WHERE sido=? AND gugun=?`) 성능 대비.
CREATE INDEX idx_accommodations_sido_gugun ON accommodations (sido, gugun);

-- V15: Accommodation 상세 DTO에 필요한 필드 5종 추가 + 기존 36행 시드 보강.
-- FE AccommodationDetailView가 사용하는 imageUrl/amenities/maxGuests/checkInTime/checkOutTime 매핑 대상.
-- reviewCount는 컬럼이 아니라 detail 조회 시 ReviewRepository.aggregateByTarget으로 산출 → 본 마이그레이션 범위 외.
-- list 응답(AccommodationResponse)은 변경 없음 — 본 컬럼은 detail-only.

ALTER TABLE accommodations
    ADD COLUMN image_url      VARCHAR(1000) NULL                                AFTER thumbnail_url,
    ADD COLUMN amenities      VARCHAR(500)  NOT NULL DEFAULT ''                 COMMENT 'comma-separated; e.g., Wi-Fi,주차,조식',
    ADD COLUMN max_guests     INT           NOT NULL DEFAULT 2                  COMMENT '최대 투숙 인원',
    ADD COLUMN check_in_time  VARCHAR(5)    NOT NULL DEFAULT '15:00'            COMMENT 'HH:mm',
    ADD COLUMN check_out_time VARCHAR(5)    NOT NULL DEFAULT '11:00'            COMMENT 'HH:mm';

-- 시드 보강: type별 합리적 default. NOT NULL/DEFAULT가 이미 깔려 있으므로 본 UPDATE는 의미 있는 값으로 덮어쓰기.
UPDATE accommodations
SET amenities  = CASE type
                     WHEN 'HOTEL'      THEN 'Wi-Fi,주차,조식,피트니스'
                     WHEN 'PENSION'    THEN 'Wi-Fi,주차,바비큐,취사'
                     WHEN 'GUESTHOUSE' THEN 'Wi-Fi,공용주방,라운지'
                     WHEN 'MOTEL'      THEN 'Wi-Fi,주차,TV'
                     ELSE ''
                 END,
    max_guests = CASE type
                     WHEN 'HOTEL'      THEN 4
                     WHEN 'PENSION'    THEN 6
                     WHEN 'GUESTHOUSE' THEN 2
                     WHEN 'MOTEL'      THEN 2
                     ELSE 2
                 END,
    image_url  = COALESCE(thumbnail_url, 'https://picsum.photos/seed/acc-default/800/600')
WHERE id > 0;

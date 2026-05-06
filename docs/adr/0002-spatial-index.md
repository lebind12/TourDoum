# ADR 0002 — 공간 인덱스 및 반경 검색 전략

- 작성일: 2026-05-06
- 상태: **Accepted**
- 작성자: Implementer-A (BE) — be/feat-domain-attraction worktree

---

## 컨텍스트

TourDoum의 핵심 기능 중 하나는 "내 주변 여행지 찾기"다.
사용자 위치(위도/경도)를 기준으로 반경 N미터 이내 여행지를 거리 순으로 반환해야 한다.

- 여행지 수: 초기 50개 시드, 향후 수만 건(한국관광공사 TourAPI 연동 시)
- 쿼리 빈도: FE 지도 화면에서 이동할 때마다 호출, 높은 빈도 예상
- 정확도: 지구 구면 거리(Haversine/Vincenty 근사) 필요 (평면 거리는 위도 변화로 오차 발생)

---

## 결정

**MySQL 8의 POINT 컬럼 + SRID 4326(WGS84) + 공간 인덱스(SPATIAL INDEX) + ST_Distance_Sphere로 반경 검색**을 채택한다.

### 구체적 구현 방식 — "lat/lng DECIMAL + GENERATED POINT" 패턴

JPA(Hibernate)에서 POINT 타입을 직접 매핑하려면 `hibernate-spatial` 의존성이 필요하다.
현재 `pom.xml`은 Flyway worktree 소유이므로 본 worktree에서 의존성 추가가 불가하다.

따라서 다음 분리 전략을 채택한다:

1. JPA 엔티티는 **`latitude DECIMAL(9,6)`** + **`longitude DECIMAL(9,6)`** 만 관리
2. DB 스키마에 **GENERATED ALWAYS AS (ST_SRID(POINT(longitude, latitude), 4326)) STORED** 가상 컬럼 `location` 추가
3. `location` 컬럼에 **SPATIAL INDEX** 생성
4. 반경 검색은 **Native Query**로 `ST_Distance_Sphere(location, ST_SRID(POINT(:lng, :lat), 4326))`

이렇게 하면 JPA는 lat/lng만 읽고 쓰며, DB가 POINT 가상 컬럼을 자동 유지한다.
공간 인덱스 혜택은 그대로 누린다.

### 컬럼 정의 (SQL)

```sql
latitude  DECIMAL(9,6) NOT NULL,
longitude DECIMAL(9,6) NOT NULL,
location  POINT GENERATED ALWAYS AS (ST_SRID(POINT(longitude, latitude), 4326)) STORED NOT NULL SRID 4326,
SPATIAL INDEX idx_attractions_location (location)
```

### 검색 쿼리 (Native Query)

```sql
SELECT *, ST_Distance_Sphere(location, ST_SRID(POINT(:lng, :lat), 4326)) AS distance
FROM attractions
WHERE ST_Distance_Sphere(location, ST_SRID(POINT(:lng, :lat), 4326)) <= :radiusMeters
ORDER BY distance
LIMIT :limit
```

---

## 대안 및 기각 이유

| 대안 | 기각 이유 |
|---|---|
| PostgreSQL + PostGIS | DB를 MySQL에서 PostgreSQL로 교체해야 함. SSAFY 프로젝트 스택 변경은 ADR-0001 부트스트랩 결정 위반 |
| Elasticsearch geo_point | 별도 검색엔진 도입 비용. 초기 50~수만 건 규모에서 과도한 인프라 |
| Google S2 / H3 라이브러리 | 애플리케이션 레이어 구현 필요, DB 인덱스 효과 없음. 수백만 건에서야 의미 있음 |
| hibernate-spatial + JTS Point | 의존성 추가 필요 (pom.xml Flyway worktree 소유). 후속 worktree에서 도입 검토 |
| DECIMAL lat/lng + 앱에서 Haversine | 풀스캔 → 인덱스 없음. 수만 건 초과 시 성능 급락 |

---

## 결과

- MySQL 8 기본 기능만 사용하므로 추가 인프라 불필요
- `hibernate-spatial` 없이도 공간 인덱스 효과 누림
- JPA 레이어는 단순 DECIMAL lat/lng 처리 → Hibernate metadata 충돌 없음
- GENERATED 컬럼은 Hibernate DDL validate에서 무시됨 (정상 동작)
- 향후 hibernate-spatial 도입 시 GENERATED 컬럼 제거하고 `@Column(columnDefinition = "POINT SRID 4326 NOT NULL")` + JTS Point 타입으로 교체 가능

---

## 참고

- MySQL 8.0 공간 데이터 함수: https://dev.mysql.com/doc/refman/8.0/en/spatial-analysis-functions.html
- ST_Distance_Sphere: 지구 반경 6370986m 기준 구면 거리(미터) 반환
- SRID 4326 = WGS84 (GPS 표준 좌표계)

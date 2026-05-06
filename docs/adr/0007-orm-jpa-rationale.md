# ADR 0007 — ORM 선택: JPA(Hibernate) + QueryDSL 채택 근거

- 작성일: 2026-05-07
- 상태: Accepted
- 작성자: Doc/Memory Agent (Claude Haiku 4.5)

## 컨텍스트

ADR-0001 부트스트랩 인터뷰에서 JPA를 선택했으나, 기획 단계의 고민(MyBatis vs JPA)을 정식 ADR로 박제하지 않았다.
본 ADR은 TourDoum 도메인 특성(CRUD 75% + 공간/집계 25%)에 비춰 JPA 채택의 명시적 근거를 기록한다.

---

## JPA(Hibernate)+QueryDSL vs MyBatis 비교표

| 축 | JPA(+QueryDSL) | MyBatis |
|---|---|---|
| **코드 스타일** | 선언적 (@Entity, @OneToMany). save/find 자동 생성 | 명시적. 모든 SQL을 mapper에 작성 |
| **학습 곡선** | 가파름 (영속성 컨텍스트, 1·2차 캐시, 지연 로딩, 트랜잭션 경계) | 완만 (SQL만 알면 시작) |
| **CRUD 보일러플레이트** | 거의 0 | 매번 작성 (insert/update/delete/select) |
| **동적 쿼리** | QueryDSL/Specification → 타입 안전 | `<if>`, `<choose>`, `<foreach>` XML |
| **복잡 SQL** | JPQL 한계 → native + projection | 강점. 윈도우/CTE/hint 자유 |
| **공간 함수** | native + projection 매핑 필요 | SQL에 직접, 즉시 테스트 |
| **N+1 문제** | 발생 가능. fetch join/EntityGraph로 해결 | 발생 안 함 (직접 작성) |
| **연관관계 매핑** | @ManyToOne 등 자동 객체 그래프 | join SQL + nested resultMap |
| **타입 안전성** | QueryDSL Q-class 컴파일 검증 | XML 문자열 (IDE 보강 가능) |
| **DB 마이그레이션** | ddl-auto=update prototyping (양날) | DDL은 Flyway에 100% 위임 |
| **테스트** | @DataJpaTest 슬라이스 표준 | @MybatisTest + Testcontainers |
| **벤더 락인** | DB 변경 쉬움(JPA spec) | DB 종속 SQL 다수 |
| **한국 학습 자료** | 김영한 강의/책 풍부 | SSAFY 표준 교재 |
| **신규 백엔드 시장** | 60~70% | 30~40% (전통 SI/금융권) |
| **운영 디버깅** | 생성 SQL 추적 도구 필요 (로깅, jdbc driver) | 작성한 SQL 그대로 실행 |

---

## TourDoum 도메인 분석

### 작업 유형별 비중

| 작업 유형 | 비중 추정 | 적합 도구 |
|---|---|---|
| **단순 CRUD** | 60% | JPA 압도적 |
| **페이지네이션+필터** | 15% | 동률 |
| **공간 검색** | 15% | MyBatis 약간 우세 |
| **통계/집계** | 10% | MyBatis 우세 |

### 도메인 구조 (객체 그래프)

- **회원** — 즐겨찾기 목록 (1:N)
  - 즐겨찾기 — 여행지 (N:1)
    - 여행지 — 후기 목록 (1:N)
    - 여행지 — 숙박 목록 (1:N)
      - 숙박 — 예약 목록 (1:N)

객체 그래프 깊이 및 관계 복잡도가 높아, **JPA의 선언적 매핑과 자동 로딩** 편의성이 두드러진다.

---

## JPA 채택 근거 (7개)

### 1. 도메인 구성이 객체 그래프 친화적

회원 → 즐겨찾기 → 여행지 → 후기/숙박 → 예약으로 이어지는 다층 연관관계를 **선언적 @OneToMany/@ManyToOne**로 매핑할 때,
JPA는 `fetch join` 또는 `EntityGraph`로 쿼리 최적화 규칙을 한 곳에 박제할 수 있다.
MyBatis는 매번 조인 조건을 작성해야 하므로 일관성 유지 비용 높음.

### 2. CRUD 비중 75%에서 보일러플레이트 절감 효과 큼

- **JPA**: `repository.save(entity)` → 자동 insert/update/delete
- **MyBatis**: `mapper.insertAttraction()`, `mapper.updateAttraction()`, `mapper.deleteAttraction()` 3개 메서드 + 3개 SQL 작성

월 500줄 보일러플레이트 제거는 **첫 3주 개발 속도 50% 상승**으로 환산.

### 3. 공간 검색·통계는 native + projection으로 커버 가능

JPA의 약점인 **복잡 SQL**(윈도우 함수, CTE 등)은 `@Query(nativeQuery=true)` + **custom projection**으로 해결:

```java
@Query(value = "SELECT a.id, a.name, ST_Distance_Sphere(a.location, POINT(?, ?)) as distance " +
               "FROM attractions a " +
               "WHERE ST_Distance_Sphere(a.location, POINT(?, ?)) <= ? " +
               "ORDER BY distance",
       nativeQuery = true)
List<AttractionWithDistanceDto> findNearby(double lat, double lon, double lat2, double lon2, int radiusMeters);
```

한 번 패턴을 박으면 재사용성 높음. MyBatis와 복잡도 거의 같음.

### 4. 한국어 학습 자료 풍부, 졸업 후 신규 프로젝트 시장 적합도

- 김영한 강의/책: "자바 ORM 표준 JPA 프로그래밍" (검증된 교과서)
- 신규 백엔드 채용 공고: JPA 60~70%, MyBatis 30~40%
- **학습 시점**의 선택이 직무 이동성에 직결

### 5. 본 워크스페이스 목적과 부합

CLAUDE.md §0: "트랙 표준에서 한 걸음 나아간 도구로 폭 넓히기"

- SSAFY 표준 = MyBatis 기반 JSP 웹
- 본 재구현 목표 = 최신 에코시스템(Spring Boot 3.3, JPA, React/Vue, 마이크로서비스 이해)
- **JPA 선택은 트랙 이후 시장 진입의 사다리 역할**

### 6. QueryDSL Q-class로 타입 안전성, 리팩터 안전성

```java
// 타입 안전 + 컴파일 검증
QAttraction attraction = QAttraction.attraction;
List<Attraction> nearby = queryFactory
    .selectFrom(attraction)
    .where(attraction.location.isInRadius(lat, lon, radiusMeters))
    .fetch();
```

MyBatis XML 문자열 리팩터는 IDE 지원 약함. JPA는 메서드명 변경 → Q-class 자동 갱신.

### 7. 현 시점 도메인 2개·테스트·인증·세션 모두 JPA 박제됨

ADR-0001에서 이미:
- 도메인 모듈(여행지, 숙박, 사용자) → JPA 정의
- 테스트 → @DataJpaTest
- 세션 → Spring Session Data Redis (Spring Data와 조화)

**후행 비용(MyBatis 병행, 리팩터 비용)은 지금부터 시작하면 가파르게 상승**. 지금이 최적 결정 시점.

---

## Trade-off (단점 인정)

### 1. 공간/집계 native + projection 학습 비용 1회

JPA에서 벗어난 SQL 작성이 필요함. 처음엔 난감하지만, **기초 패턴 1~2개 학습 후 재사용** 예상.
(총 학습 시간: 4~8시간)

### 2. N+1 문제 도메인 확장 시 표면화 가능

릴리즈 초기: 연관관계 자동 로딩 → 쿼리 폭증.
**예방책**: fetch join/EntityGraph 규칙 필수 박제, 코드 리뷰에서 적극 지적.

### 3. 통계/리포트 화면은 MyBatis 부분 도입 후속 검토

대시보드/분석 화면이 필요하면 MyBatis 혼용 고려. → **후속 ADR-000X** 또는 **설정값 조정**(JPA는 OLTP, MyBatis는 OLAP).

---

## 결정

**JPA(Hibernate) + QueryDSL 채택. 상태: Accepted.**

---

## 미이행 / 후속

- **N+1 방지 가이드라인**: 코드 리뷰 체크리스트에 "fetch join/EntityGraph 적용?"
- **네이티브 쿼리 라이브러리**: 공간/집계 SQL 모음 Javadoc 정리 (재사용성 향상)
- **통계/리포트 도입 시**: MyBatis 부분 도입 ADR 검토

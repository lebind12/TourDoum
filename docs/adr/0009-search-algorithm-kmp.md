# ADR-0009: 여행지 keyword 검색에 KMP 알고리즘 적용

- **날짜**: 2026-05-07
- **상태**: Accepted
- **결정자**: Backend Implementer (codex/feat-attraction-kmp-search)

---

## 맥락

TourDoum의 여행지 데이터는 현재 약 13,155건이다. 사용자는 `/api/attractions` 목록에서 이름 또는 주소에 포함된 keyword로 여행지를 찾을 수 있어야 한다.

이번 worktree의 목적에는 SSAFY 알고리즘 학습이 포함되어 있어, 검색 경로에 직접 구현한 substring 알고리즘을 넣어 Java 코드에서 동작을 확인할 필요가 있다.

---

## 결정

`attractions.name` 또는 `attractions.address`에 대해 SQL `LIKE`로 1차 후보를 조회하고, 서비스 계층에서 KMP(Knuth-Morris-Pratt) 알고리즘으로 keyword 포함 여부를 2차 검증한다.

```sql
SELECT *
FROM attractions
WHERE name LIKE CONCAT('%', :keyword, '%')
   OR address LIKE CONCAT('%', :keyword, '%')
ORDER BY id
LIMIT :limit
```

### 선택 이유

- DB에서 broad match를 먼저 수행해 모든 여행지를 애플리케이션 메모리로 읽는 방식을 피한다.
- KMP 구현을 서비스 흐름에 포함해 학습 목적을 충족한다.
- 기존 JPA Repository 구조에 작은 native query만 추가하면 되어 변경 범위가 작다.
- `ORDER BY id`로 후보 순서를 고정해 페이지 응답이 흔들리지 않게 한다.

---

## 구현

- `KmpMatcher`는 `contains`, `indexOf`, `buildLps` 정적 메서드를 제공한다.
- `AttractionRepository.findKeywordCandidates(keyword, limit)`가 SQL `LIKE` 후보를 반환한다.
- `AttractionService.searchByKeyword(keyword, page, size)`가 후보의 `name`/`address`를 KMP로 재검증한 뒤 페이지를 구성한다.
- `/api/attractions`는 `keyword` query parameter를 받는다.
- keyword 검색 경로에서는 후보가 이미 메모리에 있으므로 `region`/`category` 부가 필터를 함께 적용한다.

---

## 한계

SQL `LIKE '%keyword%'`도 substring match이고 KMP도 substring match이므로, 현재 구조에서 KMP는 성능상 필수 검증 단계라기보다 학습 목적이 크다. 특히 MySQL collation은 대소문자·악센트 민감도가 Java `char` 비교와 다를 수 있어 LIKE 후보 중 KMP에서 제외되는 값이 생길 수 있다.

후보 조회 한도는 현재 데이터셋 규모를 덮는 값으로 설정했다. 데이터가 크게 늘어나거나 keyword가 매우 흔한 경우에는 애플리케이션 메모리 필터링보다 DB/검색엔진 기반 설계가 적합하다.

---

## 대안

- MySQL FULLTEXT: 운영 검색에는 더 적합하지만 한글 형태소/부분 문자열 요구에는 별도 튜닝이 필요하다.
- Trigram index: 부분 문자열 검색에 강하지만 MySQL 기본 기능만으로는 도입 비용이 있다.
- Elasticsearch/OpenSearch: 대규모 검색과 랭킹에 적합하지만 인프라 운영 비용이 커진다.
- 전량 인메모리 KMP: 학습 의도는 가장 강하지만 데이터 증가 시 확장성이 낮다.

---

## 결과

목록 API는 기존 region/category 조회를 유지하면서 keyword 검색을 추가한다. 검색 정확성은 Java 단위 테스트와 서비스 Mockito 테스트로 검증하고, 운영 검색 품질 고도화는 후속 과제로 남긴다.

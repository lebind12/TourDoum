package com.ssafy.tourdoum.accommodation;

import java.util.List;
import java.util.Map;

/**
 * Accommodation 행정구역 옵션 응답 — `GET /api/accommodations/regions`.
 *
 * <p>FE 필터 컴포넌트가 시·도 select와 그에 따른 시·군·구 select를 채우는 데 사용한다. 매 list fetch마다 distinct 추출하지 않도록 별도
 * endpoint로 분리.
 *
 * @param sidos 한글 사전순 정렬된 시·도 목록.
 * @param gugunsBySido 시·도 → 시·군·구 목록(한글 사전순).
 */
public record AccommodationRegionsResponse(
    List<String> sidos, Map<String, List<String>> gugunsBySido) {}

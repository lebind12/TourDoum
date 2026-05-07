package com.ssafy.tourdoum.accommodation;

import com.ssafy.tourdoum.common.algorithm.KmpMatcher;
import com.ssafy.tourdoum.review.ReviewRepository;
import com.ssafy.tourdoum.review.ReviewTargetType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 숙박 서비스. */
@Service
@Transactional(readOnly = true)
public class AccommodationService {

  private static final int KEYWORD_CANDIDATE_LIMIT = 20_000;

  private final AccommodationRepository accommodationRepository;
  private final ReviewRepository reviewRepository;

  public AccommodationService(
      AccommodationRepository accommodationRepository, ReviewRepository reviewRepository) {
    this.accommodationRepository = accommodationRepository;
    this.reviewRepository = reviewRepository;
  }

  /**
   * 숙박 목록 조회 (페이지네이션).
   *
   * <p>keyword 검색 시 LIKE 후보를 KMP로 재검증. keyword 없으면 전체 조회.
   *
   * @param keyword 이름/주소 substring 검색어 (null 또는 blank이면 미적용)
   * @param page 페이지 번호 (0-based)
   * @param size 페이지 크기
   */
  public Page<AccommodationResponse> list(String keyword, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());

    if (keyword != null && !keyword.isBlank()) {
      return searchByKeyword(keyword, pageable);
    }

    return accommodationRepository.findAll(pageable).map(AccommodationResponse::from);
  }

  /**
   * 숙박 keyword 검색.
   *
   * <p>SQL LIKE로 후보를 좁힌 뒤 Java KMP 구현으로 이름/주소 substring 포함 여부를 재검증한다. AttractionService 동일 패턴.
   *
   * @param keyword 이름/주소 substring 검색어
   * @param page 페이지 번호 (0-based)
   * @param size 페이지 크기
   */
  public Page<AccommodationResponse> searchByKeyword(String keyword, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
    return searchByKeyword(keyword, pageable);
  }

  private Page<AccommodationResponse> searchByKeyword(String keyword, Pageable pageable) {
    if (keyword == null || keyword.isBlank()) {
      return Page.empty(pageable);
    }

    String normalizedKeyword = keyword.trim();
    int candidateLimit = keywordCandidateLimit(pageable);

    List<AccommodationResponse> matched =
        accommodationRepository.findKeywordCandidates(normalizedKeyword, candidateLimit).stream()
            .filter(accommodation -> matchesKeyword(accommodation, normalizedKeyword))
            .map(AccommodationResponse::from)
            .toList();

    return page(matched, pageable);
  }

  /**
   * 숙박 단건 상세 조회 — detail DTO 반환.
   *
   * <p>list/nearby 응답({@link AccommodationResponse})과 분리. amenities/maxGuests/checkIn 등 상세 화면 전용
   * 필드와 {@link ReviewRepository#aggregateByTarget}으로 집계한 reviewCount 를 포함한다.
   *
   * @throws AccommodationNotFoundException 해당 id가 없을 때 (GlobalExceptionHandler → 404)
   */
  public AccommodationDetailResponse getById(Long id) {
    Accommodation accommodation =
        accommodationRepository
            .findById(id)
            .orElseThrow(() -> new AccommodationNotFoundException(id));
    long reviewCount = countReviews(id);
    return AccommodationDetailResponse.from(accommodation, reviewCount);
  }

  /**
   * 본 숙박을 대상으로 작성된 후기 건수.
   *
   * <p>{@link ReviewRepository#aggregateByTarget}는 {@code [AVG(rating), COUNT(r)]} 형태이며 후기가 없으면
   * COUNT=0 을 반환한다. detail 1회당 추가 쿼리 1회.
   */
  private long countReviews(Long accommodationId) {
    Object[] aggregate =
        reviewRepository.aggregateByTarget(ReviewTargetType.ACCOMMODATION, accommodationId);
    if (aggregate == null || aggregate.length < 2 || aggregate[1] == null) {
      return 0L;
    }
    return ((Number) aggregate[1]).longValue();
  }

  /**
   * 행정구역 옵션 조회 — `GET /api/accommodations/regions`. FE 시·도/시·군·구 필터 select 옵션 소스.
   *
   * <p>{@link AccommodationRepository#findDistinctSidoGugunPairs}이 한글 사전순 (sido, gugun) 페어를 반환하므로
   * 그룹화만 수행. {@code LinkedHashMap}으로 sido 정렬 유지.
   */
  public AccommodationRegionsResponse getRegions() {
    Map<String, List<String>> mutable = new LinkedHashMap<>();
    for (Object[] pair : accommodationRepository.findDistinctSidoGugunPairs()) {
      String sido = (String) pair[0];
      String gugun = (String) pair[1];
      mutable.computeIfAbsent(sido, k -> new ArrayList<>()).add(gugun);
    }
    Map<String, List<String>> immutable = new LinkedHashMap<>();
    mutable.forEach((k, v) -> immutable.put(k, List.copyOf(v)));
    List<String> sidos = List.copyOf(immutable.keySet());
    return new AccommodationRegionsResponse(sidos, immutable);
  }

  /**
   * 주변 숙박 반경 검색.
   *
   * <p>Native Query로 ST_Distance_Sphere 사용. MySQL 8 전용이며 H2에서는 동작하지 않는다.
   *
   * @param lat 위도 (WGS84)
   * @param lng 경도 (WGS84)
   * @param radiusMeters 반경(미터)
   * @param limit 최대 건수
   */
  public List<AccommodationResponse> findNearby(
      double lat, double lng, int radiusMeters, int limit) {
    return accommodationRepository.findNearby(lat, lng, radiusMeters, limit).stream()
        .map(AccommodationResponse::from)
        .toList();
  }

  private int keywordCandidateLimit(Pageable pageable) {
    long minimumForRequestedPage = pageable.getOffset() + pageable.getPageSize();
    long limit = Math.max(KEYWORD_CANDIDATE_LIMIT, minimumForRequestedPage);
    return (int) Math.min(limit, Integer.MAX_VALUE);
  }

  private boolean matchesKeyword(Accommodation accommodation, String keyword) {
    return contains(accommodation.getName(), keyword)
        || contains(accommodation.getAddress(), keyword);
  }

  private boolean contains(String text, String keyword) {
    return text != null && KmpMatcher.contains(text, keyword);
  }

  private Page<AccommodationResponse> page(List<AccommodationResponse> content, Pageable pageable) {
    if (pageable.getOffset() >= content.size()) {
      return new PageImpl<>(List.of(), pageable, content.size());
    }

    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), content.size());
    return new PageImpl<>(content.subList(start, end), pageable, content.size());
  }
}

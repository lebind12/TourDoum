package com.ssafy.tourdoum.accommodation;

import com.ssafy.tourdoum.common.algorithm.KmpMatcher;
import java.util.List;
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

  public AccommodationService(AccommodationRepository accommodationRepository) {
    this.accommodationRepository = accommodationRepository;
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
   * 숙박 단건 조회.
   *
   * @throws AccommodationNotFoundException 해당 id가 없을 때 (GlobalExceptionHandler → 404)
   */
  public AccommodationResponse getById(Long id) {
    Accommodation accommodation =
        accommodationRepository
            .findById(id)
            .orElseThrow(() -> new AccommodationNotFoundException(id));
    return AccommodationResponse.from(accommodation);
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

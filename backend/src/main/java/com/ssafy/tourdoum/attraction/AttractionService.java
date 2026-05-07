package com.ssafy.tourdoum.attraction;

import com.ssafy.tourdoum.common.algorithm.KmpMatcher;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 여행지 서비스. */
@Service
@Transactional(readOnly = true)
public class AttractionService {

  private static final int KEYWORD_CANDIDATE_LIMIT = 20_000;

  private final AttractionRepository attractionRepository;

  public AttractionService(AttractionRepository attractionRepository) {
    this.attractionRepository = attractionRepository;
  }

  /**
   * 여행지 목록 조회 (페이지네이션).
   *
   * <p>region + category 복합 필터는 현재 미지원 (QueryDSL 미도입 단계). 둘 중 하나만 적용된다: region 우선. TODO: 후속
   * worktree에서 QueryDSL BooleanBuilder로 복합 조건 검색 구현 권장. 단, keyword 검색 경로에서는 LIKE 후보를 메모리에서 KMP로
   * 재검증하므로 region/category 부가 필터를 함께 적용할 수 있다.
   *
   * @param region 지역 필터 (null이면 전체)
   * @param category 카테고리 필터 (null이면 전체, region이 있으면 무시됨)
   * @param keyword 이름/주소 substring 검색어 (null 또는 blank이면 미적용)
   * @param page 페이지 번호 (0-based)
   * @param size 페이지 크기
   */
  public Page<AttractionResponse> list(
      String region, AttractionCategory category, String keyword, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());

    if (keyword != null && !keyword.isBlank()) {
      return searchByKeyword(keyword, region, category, pageable);
    }

    if (region != null && !region.isBlank()) {
      return attractionRepository.findByRegion(region, pageable).map(AttractionResponse::from);
    }

    if (category != null) {
      return attractionRepository.findByCategory(category, pageable).map(AttractionResponse::from);
    }

    return attractionRepository.findAll(pageable).map(AttractionResponse::from);
  }

  /**
   * 여행지 keyword 검색.
   *
   * <p>SQL LIKE로 후보를 좁힌 뒤 Java KMP 구현으로 이름/주소 substring 포함 여부를 재검증한다.
   *
   * @param keyword 이름/주소 substring 검색어
   * @param page 페이지 번호 (0-based)
   * @param size 페이지 크기
   */
  public Page<AttractionResponse> searchByKeyword(String keyword, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
    return searchByKeyword(keyword, null, null, pageable);
  }

  private Page<AttractionResponse> searchByKeyword(
      String keyword, String region, AttractionCategory category, Pageable pageable) {
    if (keyword == null || keyword.isBlank()) {
      return Page.empty(pageable);
    }

    String normalizedKeyword = keyword.trim();
    List<AttractionResponse> matched =
        attractionRepository
            .findKeywordCandidates(normalizedKeyword, keywordCandidateLimit(pageable))
            .stream()
            .filter(attraction -> matchesKeyword(attraction, normalizedKeyword))
            .filter(attraction -> matchesRegion(attraction, region))
            .filter(attraction -> matchesCategory(attraction, category))
            .map(AttractionResponse::from)
            .toList();

    return page(matched, pageable);
  }

  /**
   * 여행지 단건 조회.
   *
   * @throws AttractionNotFoundException 해당 id가 없을 때 (GlobalExceptionHandler → 404)
   */
  public AttractionResponse getById(Long id) {
    Attraction attraction =
        attractionRepository.findById(id).orElseThrow(() -> new AttractionNotFoundException(id));
    return AttractionResponse.from(attraction);
  }

  /**
   * 주변 여행지 반경 검색.
   *
   * <p>Native Query로 ST_Distance_Sphere 사용. MySQL 8 전용이며 H2에서는 동작하지 않는다.
   *
   * @param lat 위도 (WGS84)
   * @param lng 경도 (WGS84)
   * @param radiusMeters 반경(미터)
   * @param limit 최대 건수
   */
  public List<AttractionResponse> findNearby(double lat, double lng, int radiusMeters, int limit) {
    return attractionRepository.findNearby(lat, lng, radiusMeters, limit).stream()
        .map(AttractionResponse::from)
        .toList();
  }

  private int keywordCandidateLimit(Pageable pageable) {
    long minimumForRequestedPage = pageable.getOffset() + pageable.getPageSize();
    long limit = Math.max(KEYWORD_CANDIDATE_LIMIT, minimumForRequestedPage);
    return (int) Math.min(limit, Integer.MAX_VALUE);
  }

  private boolean matchesKeyword(Attraction attraction, String keyword) {
    return contains(attraction.getName(), keyword) || contains(attraction.getAddress(), keyword);
  }

  private boolean contains(String text, String keyword) {
    return text != null && KmpMatcher.contains(text, keyword);
  }

  private boolean matchesRegion(Attraction attraction, String region) {
    return region == null || region.isBlank() || attraction.getRegion().equals(region.trim());
  }

  private boolean matchesCategory(Attraction attraction, AttractionCategory category) {
    return category == null || attraction.getCategory() == category;
  }

  private Page<AttractionResponse> page(List<AttractionResponse> content, Pageable pageable) {
    if (pageable.getOffset() >= content.size()) {
      return new PageImpl<>(List.of(), pageable, content.size());
    }

    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), content.size());
    return new PageImpl<>(content.subList(start, end), pageable, content.size());
  }
}

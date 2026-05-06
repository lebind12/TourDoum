package com.ssafy.tourdoum.attraction;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 여행지 서비스. */
@Service
@Transactional(readOnly = true)
public class AttractionService {

  private final AttractionRepository attractionRepository;

  public AttractionService(AttractionRepository attractionRepository) {
    this.attractionRepository = attractionRepository;
  }

  /**
   * 여행지 목록 조회 (페이지네이션).
   *
   * <p>region + category 복합 필터는 현재 미지원 (QueryDSL 미도입 단계). 둘 중 하나만 적용된다: region 우선. TODO: 후속
   * worktree에서 QueryDSL BooleanBuilder로 복합 조건 검색 구현 권장.
   *
   * @param region 지역 필터 (null이면 전체)
   * @param category 카테고리 필터 (null이면 전체, region이 있으면 무시됨)
   * @param page 페이지 번호 (0-based)
   * @param size 페이지 크기
   */
  public Page<AttractionResponse> list(
      String region, AttractionCategory category, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());

    if (region != null && !region.isBlank()) {
      return attractionRepository.findByRegion(region, pageable).map(AttractionResponse::from);
    }

    if (category != null) {
      return attractionRepository.findByCategory(category, pageable).map(AttractionResponse::from);
    }

    return attractionRepository.findAll(pageable).map(AttractionResponse::from);
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
}

package com.ssafy.tourdoum.attraction;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행지 REST 컨트롤러.
 *
 * <p>인증 정책: 현재 SecurityConfig에서 /api/** 는 authenticated() 기본. 공개 데이터이므로 /api/attractions/** 를
 * permitAll()로 열어야 한다. SecurityConfig는 다른 worktree 소유이므로 본 worktree에서 변경하지 않는다. handoff.md에 결정 사항으로
 * 기록.
 */
@RestController
@RequestMapping("/api/attractions")
public class AttractionController {

  private final AttractionService attractionService;

  public AttractionController(AttractionService attractionService) {
    this.attractionService = attractionService;
  }

  /**
   * 여행지 목록 조회 (페이지네이션).
   *
   * @param region 지역 필터 (선택)
   * @param category 카테고리 필터 (선택, region이 있으면 무시)
   * @param page 페이지 번호 (기본 0)
   * @param size 페이지 크기 (기본 20)
   */
  @GetMapping
  public ResponseEntity<PageResponse<AttractionResponse>> list(
      @RequestParam(required = false) String region,
      @RequestParam(required = false) AttractionCategory category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<AttractionResponse> result = attractionService.list(region, category, page, size);
    return ResponseEntity.ok(PageResponse.from(result));
  }

  /**
   * 여행지 단건 조회.
   *
   * @param id 여행지 PK
   */
  @GetMapping("/{id}")
  public ResponseEntity<AttractionResponse> getById(@PathVariable Long id) {
    return ResponseEntity.ok(attractionService.getById(id));
  }

  /**
   * 주변 여행지 반경 검색.
   *
   * @param lat 위도 (WGS84)
   * @param lng 경도 (WGS84)
   * @param radius 반경 미터 (기본 5000)
   * @param limit 최대 건수 (기본 20)
   */
  @GetMapping("/nearby")
  public ResponseEntity<List<AttractionResponse>> nearby(
      @RequestParam double lat,
      @RequestParam double lng,
      @RequestParam(defaultValue = "5000") int radius,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(attractionService.findNearby(lat, lng, radius, limit));
  }
}

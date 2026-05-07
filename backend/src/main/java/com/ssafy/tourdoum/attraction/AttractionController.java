package com.ssafy.tourdoum.attraction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * <p>인증 정책: SecurityConfig에서 /api/attractions/** 는 permitAll.
 */
@Tag(name = "Attraction", description = "여행지 조회 API (목록·단건·주변 검색)")
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
  @Operation(
      summary = "여행지 목록 조회",
      description = "지역 또는 카테고리 필터를 적용해 여행지 목록을 페이지 단위로 조회한다. 인증 불필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "여행지 목록 (페이지 정보 포함)"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터 (category 값 오류 등)")
  })
  @GetMapping
  public ResponseEntity<PageResponse<AttractionResponse>> list(
      @Parameter(description = "지역 필터 (예: 서울)") @RequestParam(required = false) String region,
      @Parameter(description = "카테고리 필터 (NATURE|HISTORY|ACTIVITY|FOOD|SHOPPING|OTHER)")
          @RequestParam(required = false)
          AttractionCategory category,
      @Parameter(description = "페이지 번호 (0-based, 기본 0)") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "페이지 크기 (기본 20, 최대 100)") @RequestParam(defaultValue = "20")
          int size) {
    Page<AttractionResponse> result = attractionService.list(region, category, page, size);
    return ResponseEntity.ok(PageResponse.from(result));
  }

  /**
   * 여행지 단건 조회.
   *
   * @param id 여행지 PK
   */
  @Operation(summary = "여행지 단건 조회", description = "PK로 여행지 상세 정보를 조회한다. 인증 불필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "여행지 상세 정보"),
    @ApiResponse(responseCode = "404", description = "해당 ID의 여행지를 찾을 수 없음")
  })
  @GetMapping("/{id}")
  public ResponseEntity<AttractionResponse> getById(
      @Parameter(description = "여행지 PK") @PathVariable Long id) {
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
  @Operation(
      summary = "주변 여행지 검색",
      description = "위도·경도 중심점 기준 반경(m) 안의 여행지를 거리 오름차순으로 반환한다. 인증 불필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "반경 내 여행지 목록 (거리 오름차순)"),
    @ApiResponse(responseCode = "400", description = "좌표 파라미터 누락 또는 형식 오류")
  })
  @GetMapping("/nearby")
  public ResponseEntity<List<AttractionResponse>> nearby(
      @Parameter(description = "위도 (WGS84, 예: 37.5665)", required = true) @RequestParam double lat,
      @Parameter(description = "경도 (WGS84, 예: 126.9780)", required = true) @RequestParam double lng,
      @Parameter(description = "검색 반경 미터 (기본 5000)") @RequestParam(defaultValue = "5000")
          int radius,
      @Parameter(description = "최대 결과 건수 (기본 20)") @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(attractionService.findNearby(lat, lng, radius, limit));
  }
}

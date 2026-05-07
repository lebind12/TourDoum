package com.ssafy.tourdoum.accommodation;

import com.ssafy.tourdoum.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 숙박 REST 컨트롤러.
 *
 * <p>인증 정책: SecurityConfig에서 /api/accommodations/** 는 permitAll.
 */
@Tag(name = "Accommodation", description = "숙박 조회 API (목록·단건·주변 검색)")
@Validated
@RestController
@RequestMapping("/api/accommodations")
public class AccommodationController {

  private final AccommodationService accommodationService;

  public AccommodationController(AccommodationService accommodationService) {
    this.accommodationService = accommodationService;
  }

  /**
   * 숙박 목록 조회 (페이지네이션 + keyword 검색).
   *
   * @param q 이름/주소 substring 검색어 (선택)
   * @param lat 위도 — 주변 검색 모드에서만 사용 (선택)
   * @param lng 경도 — 주변 검색 모드에서만 사용 (선택)
   * @param radius 반경 미터 — lat·lng 함께 전달 시 주변 검색 모드로 전환 (기본 5000)
   * @param page 페이지 번호 (기본 0)
   * @param size 페이지 크기 (기본 20)
   */
  @Operation(
      summary = "숙박 목록 조회",
      description =
          "keyword(q)로 이름/주소 substring 검색을 적용해 숙박 목록을 페이지 단위로 조회한다."
              + " lat·lng 파라미터를 함께 전달하면 주변 숙박 검색으로 전환된다. 인증 불필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "숙박 목록 (페이지 정보 포함 또는 주변 검색 목록)"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터")
  })
  @GetMapping
  public ResponseEntity<?> list(
      @Parameter(description = "이름/주소 keyword substring 검색어 (예: 해운대)")
          @RequestParam(required = false)
          String q,
      @Parameter(description = "위도 (WGS84) — 주변 검색 시 lng 와 함께 필수") @RequestParam(required = false)
          Double lat,
      @Parameter(description = "경도 (WGS84) — 주변 검색 시 lat 와 함께 필수") @RequestParam(required = false)
          Double lng,
      @Parameter(description = "검색 반경 미터 (기본 5000, 주변 검색 모드에서만 사용)")
          @RequestParam(defaultValue = "5000")
          int radius,
      @Parameter(description = "페이지 번호 (0-based, 기본 0)") @RequestParam(defaultValue = "0") @Min(0)
          int page,
      @Parameter(description = "페이지 크기 (기본 20, 최대 100)")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size) {

    // lat + lng 모두 전달 → 주변 검색 모드
    if (lat != null && lng != null) {
      List<AccommodationResponse> nearby = accommodationService.findNearby(lat, lng, radius, size);
      return ResponseEntity.ok(nearby);
    }

    Page<AccommodationResponse> result = accommodationService.list(q, page, size);
    return ResponseEntity.ok(PageResponse.from(result));
  }

  /**
   * 숙박 단건 조회.
   *
   * @param id 숙박 PK
   */
  @Operation(summary = "숙박 단건 조회", description = "PK로 숙박 상세 정보를 조회한다. 인증 불필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "숙박 상세 정보"),
    @ApiResponse(responseCode = "404", description = "해당 ID의 숙박을 찾을 수 없음")
  })
  @GetMapping("/{id}")
  public ResponseEntity<AccommodationResponse> getById(
      @Parameter(description = "숙박 PK") @PathVariable Long id) {
    return ResponseEntity.ok(accommodationService.getById(id));
  }
}

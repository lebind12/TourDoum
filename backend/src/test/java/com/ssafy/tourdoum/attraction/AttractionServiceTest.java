package com.ssafy.tourdoum.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

/** AttractionService 단위 테스트 (Mockito). */
@ExtendWith(MockitoExtension.class)
class AttractionServiceTest {

  @Mock private AttractionRepository attractionRepository;

  @InjectMocks private AttractionService attractionService;

  private Attraction buildAttraction(Long id) {
    return buildAttraction("경복궁", "서울", AttractionCategory.HISTORY, "서울특별시 종로구 사직로 161");
  }

  private Attraction buildAttraction(
      String name, String region, AttractionCategory category, String address) {
    return Attraction.builder()
        .name(name)
        .region(region)
        .category(category)
        .address(address)
        .latitude(new BigDecimal("37.579617"))
        .longitude(new BigDecimal("126.977041"))
        .description("조선 왕조 최대의 법궁")
        .build();
  }

  @Test
  @DisplayName("getById 정상 — id가 존재하면 AttractionResponse 반환")
  void getById_success() {
    // given
    Long id = 1L;
    Attraction attraction = buildAttraction(id);
    given(attractionRepository.findById(id)).willReturn(Optional.of(attraction));

    // when
    AttractionResponse response = attractionService.getById(id);

    // then
    assertThat(response.name()).isEqualTo("경복궁");
    assertThat(response.region()).isEqualTo("서울");
    assertThat(response.category()).isEqualTo(AttractionCategory.HISTORY);
    assertThat(response.distanceMeters()).isNull();
  }

  @Test
  @DisplayName("getById 예외 — id가 없으면 AttractionNotFoundException 발생")
  void getById_notFound() {
    // given
    Long id = 999L;
    given(attractionRepository.findById(id)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> attractionService.getById(id))
        .isInstanceOf(AttractionNotFoundException.class)
        .hasMessageContaining("999");
  }

  @Test
  @DisplayName("findNearby — projection의 distance가 AttractionResponse.distanceMeters에 매핑됨")
  void findNearby_maps_distance_from_projection() {
    // given
    AttractionWithDistance projection = mock(AttractionWithDistance.class);
    given(projection.getId()).willReturn(1L);
    given(projection.getName()).willReturn("경복궁");
    given(projection.getRegion()).willReturn("서울");
    given(projection.getCategory()).willReturn("HISTORY");
    given(projection.getAddress()).willReturn("서울특별시 종로구 사직로 161");
    given(projection.getLatitude()).willReturn(new BigDecimal("37.579617"));
    given(projection.getLongitude()).willReturn(new BigDecimal("126.977041"));
    given(projection.getDescription()).willReturn("조선 왕조 최대의 법궁");
    given(projection.getImageUrl()).willReturn(null);
    given(projection.getDistance()).willReturn(350.5);
    given(attractionRepository.findNearby(37.58, 126.98, 1000, 10)).willReturn(List.of(projection));

    // when
    List<AttractionResponse> result = attractionService.findNearby(37.58, 126.98, 1000, 10);

    // then
    assertThat(result).hasSize(1);
    AttractionResponse response = result.get(0);
    assertThat(response.name()).isEqualTo("경복궁");
    assertThat(response.category()).isEqualTo(AttractionCategory.HISTORY);
    assertThat(response.distanceMeters()).isEqualTo(350.5);
  }

  @Test
  @DisplayName("searchByKeyword — LIKE 후보를 KMP로 정확 검증해 이름 매칭만 반환")
  void searchByKeyword_filters_candidates_with_kmp() {
    // given
    Attraction matched =
        buildAttraction("경복궁", "서울", AttractionCategory.HISTORY, "서울특별시 종로구 사직로 161");
    Attraction notMatched =
        buildAttraction("창덕궁", "서울", AttractionCategory.HISTORY, "서울특별시 종로구 율곡로 99");
    given(attractionRepository.findKeywordCandidates("경복", 20_000))
        .willReturn(List.of(matched, notMatched));

    // when
    Page<AttractionResponse> result = attractionService.searchByKeyword("경복", 0, 20);

    // then
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent()).extracting(AttractionResponse::name).containsExactly("경복궁");
    then(attractionRepository).should().findKeywordCandidates("경복", 20_000);
  }

  @Test
  @DisplayName("list keyword — 주소 부분 매칭 후 region/category 부가 필터를 적용")
  void list_withKeyword_filters_address_match_by_region_and_category() {
    // given
    Attraction matched =
        buildAttraction("경복궁", "서울", AttractionCategory.HISTORY, "서울특별시 종로구 사직로 161");
    Attraction otherRegion =
        buildAttraction("부산 사직야구장", "부산", AttractionCategory.ACTIVITY, "부산광역시 동래구 사직로");
    Attraction otherCategory =
        buildAttraction("사직공원", "서울", AttractionCategory.NATURE, "서울특별시 종로구 사직로");
    given(attractionRepository.findKeywordCandidates("사직로", 20_000))
        .willReturn(List.of(matched, otherRegion, otherCategory));

    // when
    Page<AttractionResponse> result =
        attractionService.list("서울", AttractionCategory.HISTORY, "사직로", 0, 20);

    // then
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent()).extracting(AttractionResponse::name).containsExactly("경복궁");
    then(attractionRepository).should().findKeywordCandidates("사직로", 20_000);
  }
}

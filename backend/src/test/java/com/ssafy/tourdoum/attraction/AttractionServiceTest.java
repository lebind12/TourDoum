package com.ssafy.tourdoum.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
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

/** AttractionService 단위 테스트 (Mockito). */
@ExtendWith(MockitoExtension.class)
class AttractionServiceTest {

  @Mock private AttractionRepository attractionRepository;

  @InjectMocks private AttractionService attractionService;

  private Attraction buildAttraction(Long id) {
    return Attraction.builder()
        .name("경복궁")
        .region("서울")
        .category(AttractionCategory.HISTORY)
        .address("서울특별시 종로구 사직로 161")
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
}

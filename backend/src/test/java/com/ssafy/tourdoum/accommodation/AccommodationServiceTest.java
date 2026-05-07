package com.ssafy.tourdoum.accommodation;

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

/** AccommodationService 단위 테스트 (Mockito). 학습 친화 모드 — 양산 금지. */
@ExtendWith(MockitoExtension.class)
class AccommodationServiceTest {

  @Mock private AccommodationRepository accommodationRepository;

  @InjectMocks private AccommodationService accommodationService;

  private Accommodation buildAccommodation(String name, AccommodationType type, String address) {
    return Accommodation.builder()
        .name(name)
        .type(type)
        .address(address)
        .lat(new BigDecimal("37.5636"))
        .lng(new BigDecimal("126.9826"))
        .priceFrom(150000)
        .rating(new BigDecimal("4.3"))
        .thumbnailUrl("https://picsum.photos/seed/test/400/300")
        .description("테스트용 숙박")
        .build();
  }

  @Test
  @DisplayName("getById 정상 — id가 존재하면 AccommodationResponse 반환")
  void getById_success() {
    // given
    Long id = 1L;
    Accommodation accommodation =
        buildAccommodation("명동 호텔", AccommodationType.HOTEL, "서울특별시 중구 명동길 33");
    given(accommodationRepository.findById(id)).willReturn(Optional.of(accommodation));

    // when
    AccommodationResponse response = accommodationService.getById(id);

    // then
    assertThat(response.name()).isEqualTo("명동 호텔");
    assertThat(response.type()).isEqualTo(AccommodationType.HOTEL);
    assertThat(response.distanceMeters()).isNull();
  }

  @Test
  @DisplayName("getById 예외 — id가 없으면 AccommodationNotFoundException 발생")
  void getById_notFound() {
    // given
    Long id = 999L;
    given(accommodationRepository.findById(id)).willReturn(Optional.empty());

    // when / then
    assertThatThrownBy(() -> accommodationService.getById(id))
        .isInstanceOf(AccommodationNotFoundException.class)
        .hasMessageContaining("999");
  }

  @Test
  @DisplayName("findNearby — projection의 distance가 AccommodationResponse.distanceMeters에 매핑됨")
  void findNearby_maps_distance_from_projection() {
    // given
    AccommodationWithDistance projection = mock(AccommodationWithDistance.class);
    given(projection.getId()).willReturn(1L);
    given(projection.getName()).willReturn("명동 호텔");
    given(projection.getType()).willReturn("HOTEL");
    given(projection.getAddress()).willReturn("서울특별시 중구 명동길 33");
    given(projection.getLat()).willReturn(new BigDecimal("37.5636"));
    given(projection.getLng()).willReturn(new BigDecimal("126.9826"));
    given(projection.getPriceFrom()).willReturn(150000);
    given(projection.getRating()).willReturn(new BigDecimal("4.3"));
    given(projection.getThumbnailUrl()).willReturn(null);
    given(projection.getDescription()).willReturn("테스트용 숙박");
    given(projection.getDistance()).willReturn(500.0);
    given(accommodationRepository.findNearby(37.56, 126.98, 1000, 10))
        .willReturn(List.of(projection));

    // when
    List<AccommodationResponse> result = accommodationService.findNearby(37.56, 126.98, 1000, 10);

    // then
    assertThat(result).hasSize(1);
    AccommodationResponse response = result.get(0);
    assertThat(response.name()).isEqualTo("명동 호텔");
    assertThat(response.type()).isEqualTo(AccommodationType.HOTEL);
    assertThat(response.distanceMeters()).isEqualTo(500.0);
  }

  @Test
  @DisplayName("searchByKeyword — LIKE 후보를 KMP로 정확 검증해 이름 매칭만 반환")
  void searchByKeyword_filters_candidates_with_kmp() {
    // given
    Accommodation matched =
        buildAccommodation("해운대 씨뷰 호텔", AccommodationType.HOTEL, "부산광역시 해운대구 해운대해변로 264");
    Accommodation notMatched =
        buildAccommodation("광안리 펜션", AccommodationType.PENSION, "부산광역시 수영구 광안해변로 185");
    given(accommodationRepository.findKeywordCandidates("해운대", 20_000))
        .willReturn(List.of(matched, notMatched));

    // when
    Page<AccommodationResponse> result = accommodationService.searchByKeyword("해운대", 0, 20);

    // then
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent())
        .extracting(AccommodationResponse::name)
        .containsExactly("해운대 씨뷰 호텔");
    then(accommodationRepository).should().findKeywordCandidates("해운대", 20_000);
  }
}

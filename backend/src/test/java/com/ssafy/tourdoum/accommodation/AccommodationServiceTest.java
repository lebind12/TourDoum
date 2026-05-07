package com.ssafy.tourdoum.accommodation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.ssafy.tourdoum.review.ReviewRepository;
import com.ssafy.tourdoum.review.ReviewTargetType;
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

  @Mock private ReviewRepository reviewRepository;

  @InjectMocks private AccommodationService accommodationService;

  private Accommodation buildAccommodation(String name, AccommodationType type, String address) {
    return Accommodation.builder()
        .name(name)
        .type(type)
        .address(address)
        .sido("서울특별시")
        .gugun("중구")
        .lat(new BigDecimal("37.5636"))
        .lng(new BigDecimal("126.9826"))
        .priceFrom(150000)
        .rating(new BigDecimal("4.3"))
        .thumbnailUrl("https://picsum.photos/seed/test/400/300")
        .imageUrl("https://picsum.photos/seed/test-hero/800/600")
        .amenities("Wi-Fi,주차,조식,피트니스")
        .maxGuests(4)
        .checkInTime("15:00")
        .checkOutTime("11:00")
        .description("테스트용 숙박")
        .build();
  }

  @Test
  @DisplayName("getRegions — repository 페어를 한글 사전순 sidos + gugunsBySido로 그룹화")
  void getRegions_groups_pairs_by_sido() {
    // given — repository는 (sido, gugun) ASCENDING 정렬 페어 반환
    given(accommodationRepository.findDistinctSidoGugunPairs())
        .willReturn(
            List.of(
                new Object[] {"부산광역시", "기장군"},
                new Object[] {"부산광역시", "해운대구"},
                new Object[] {"서울특별시", "강남구"},
                new Object[] {"서울특별시", "중구"}));

    // when
    AccommodationRegionsResponse response = accommodationService.getRegions();

    // then
    assertThat(response.sidos()).containsExactly("부산광역시", "서울특별시");
    assertThat(response.gugunsBySido().get("부산광역시")).containsExactly("기장군", "해운대구");
    assertThat(response.gugunsBySido().get("서울특별시")).containsExactly("강남구", "중구");
  }

  @Test
  @DisplayName("getById 정상 — id가 존재하면 detail DTO + reviewCount 반환")
  void getById_success() {
    // given
    Long id = 1L;
    Accommodation accommodation =
        buildAccommodation("명동 호텔", AccommodationType.HOTEL, "서울특별시 중구 명동길 33");
    given(accommodationRepository.findById(id)).willReturn(Optional.of(accommodation));
    // ReviewRepository.aggregateByTarget — [AVG(rating), COUNT(r)]
    given(reviewRepository.aggregateByTarget(ReviewTargetType.ACCOMMODATION, id))
        .willReturn(new Object[] {new BigDecimal("4.5"), 7L});

    // when
    AccommodationDetailResponse response = accommodationService.getById(id);

    // then
    assertThat(response.name()).isEqualTo("명동 호텔");
    assertThat(response.type()).isEqualTo(AccommodationType.HOTEL);
    assertThat(response.imageUrl()).isEqualTo("https://picsum.photos/seed/test-hero/800/600");
    assertThat(response.amenities()).containsExactly("Wi-Fi", "주차", "조식", "피트니스");
    assertThat(response.maxGuests()).isEqualTo(4);
    assertThat(response.checkInTime()).isEqualTo("15:00");
    assertThat(response.checkOutTime()).isEqualTo("11:00");
    assertThat(response.reviewCount()).isEqualTo(7L);
  }

  @Test
  @DisplayName("getById — 후기 0건이면 reviewCount=0, amenities 비면 빈 리스트")
  void getById_zeroReviews_emptyAmenities() {
    // given
    Long id = 2L;
    Accommodation accommodation =
        Accommodation.builder()
            .name("이태원 게스트하우스")
            .type(AccommodationType.GUESTHOUSE)
            .address("서울특별시 용산구 이태원로 142")
            .lat(new BigDecimal("37.5347"))
            .lng(new BigDecimal("126.9940"))
            .amenities("") // 빈 amenities
            .maxGuests(2)
            .checkInTime("16:00")
            .checkOutTime("10:00")
            .build();
    given(accommodationRepository.findById(id)).willReturn(Optional.of(accommodation));
    // 후기 0건: AVG=null, COUNT=0
    given(reviewRepository.aggregateByTarget(ReviewTargetType.ACCOMMODATION, id))
        .willReturn(new Object[] {null, 0L});

    // when
    AccommodationDetailResponse response = accommodationService.getById(id);

    // then
    assertThat(response.amenities()).isEmpty();
    assertThat(response.reviewCount()).isEqualTo(0L);
    assertThat(response.maxGuests()).isEqualTo(2);
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
    given(projection.getSido()).willReturn("서울특별시");
    given(projection.getGugun()).willReturn("중구");
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

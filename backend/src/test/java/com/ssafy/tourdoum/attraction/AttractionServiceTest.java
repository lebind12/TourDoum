package com.ssafy.tourdoum.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
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
}

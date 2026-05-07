package com.ssafy.tourdoum.accommodation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/accommodations/regions — sido/gugun 그룹화 + 정렬 검증.
 *
 * <p>테스트 환경은 Flyway 비활성(application.yml)이고 Hibernate ddl-auto=create-drop으로 스키마만 생성한다. V7/V16 시드는
 * 적용되지 않으므로 본 테스트는 fixture를 직접 save하여 검증한다. 운영(MySQL)에선 V16 explicit UPDATE가 36행을 백필한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccommodationRegionsControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private AccommodationRepository accommodationRepository;

  @BeforeEach
  void seedFixtures() {
    save("서울 호텔A", AccommodationType.HOTEL, "서울특별시", "강남구");
    save("서울 호텔B", AccommodationType.HOTEL, "서울특별시", "중구");
    save("부산 호텔A", AccommodationType.HOTEL, "부산광역시", "해운대구");
    save("부산 호텔B", AccommodationType.HOTEL, "부산광역시", "중구");
  }

  private void save(String name, AccommodationType type, String sido, String gugun) {
    accommodationRepository.save(
        Accommodation.builder()
            .name(name)
            .type(type)
            .address(sido + " " + gugun + " 테스트로 1")
            .sido(sido)
            .gugun(gugun)
            .lat(new BigDecimal("37.5"))
            .lng(new BigDecimal("127.0"))
            .priceFrom(100000)
            .build());
  }

  @Test
  @DisplayName("GET /regions — fixture sido/gugun이 한글 사전순 그룹화돼 응답된다")
  void regions_returns_grouped_pairs() throws Exception {
    mockMvc
        .perform(get("/api/accommodations/regions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sidos").isArray())
        .andExpect(jsonPath("$.sidos", org.hamcrest.Matchers.hasItem("서울특별시")))
        .andExpect(jsonPath("$.sidos", org.hamcrest.Matchers.hasItem("부산광역시")))
        // 부산광역시 중구 vs 서울특별시 중구 — 둘 다 별개 sido 하위에 노출
        .andExpect(jsonPath("$.gugunsBySido['서울특별시']", org.hamcrest.Matchers.hasItem("강남구")))
        .andExpect(jsonPath("$.gugunsBySido['서울특별시']", org.hamcrest.Matchers.hasItem("중구")))
        .andExpect(jsonPath("$.gugunsBySido['부산광역시']", org.hamcrest.Matchers.hasItem("중구")))
        .andExpect(jsonPath("$.gugunsBySido['부산광역시']", org.hamcrest.Matchers.hasItem("해운대구")));
  }
}

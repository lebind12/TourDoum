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
 * AccommodationController GET /api/accommodations/{id} 통합 테스트 — H2 + V7 시드 + V15 보강.
 *
 * <p>Detail DTO 신규 키(imageUrl, amenities, maxGuests, checkInTime, checkOutTime, reviewCount)가 응답에
 * 포함되는지 검증한다. Docker 불필요(ApplicationContextSmokeTest와 동일 패턴).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccommodationDetailControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private AccommodationRepository accommodationRepository;

  /** 본 테스트 전용 HOTEL을 직접 저장 — V7 seed id 의존 회피 (테스트 간 H2 상태 공유 영향). */
  private Long fixtureId;

  @BeforeEach
  void seedFixture() {
    Accommodation hotel =
        Accommodation.builder()
            .name("디테일 테스트 호텔")
            .type(AccommodationType.HOTEL)
            .address("서울특별시 중구 테스트로 1")
            .sido("서울특별시")
            .gugun("중구")
            .lat(new BigDecimal("37.5636"))
            .lng(new BigDecimal("126.9826"))
            .priceFrom(180000)
            .rating(new BigDecimal("4.5"))
            .thumbnailUrl("https://picsum.photos/seed/detail-test/400/300")
            .imageUrl("https://picsum.photos/seed/detail-test-hero/800/600")
            .amenities("Wi-Fi,주차,조식,피트니스")
            .maxGuests(4)
            .checkInTime("15:00")
            .checkOutTime("11:00")
            .description("디테일 검증용")
            .build();
    fixtureId = accommodationRepository.save(hotel).getId();
  }

  @Test
  @DisplayName("GET /api/accommodations/{id} — V15 detail 필드 6종이 응답에 포함된다")
  void getById_includesDetailFields() throws Exception {
    mockMvc
        .perform(get("/api/accommodations/{id}", fixtureId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(fixtureId))
        .andExpect(jsonPath("$.name").value("디테일 테스트 호텔"))
        .andExpect(jsonPath("$.type").value("HOTEL"))
        // 신규 detail 필드 6종 — 본 #48의 핵심 검증 포인트
        .andExpect(
            jsonPath("$.imageUrl").value("https://picsum.photos/seed/detail-test-hero/800/600"))
        .andExpect(jsonPath("$.amenities").isArray())
        .andExpect(jsonPath("$.amenities", org.hamcrest.Matchers.hasItem("Wi-Fi")))
        .andExpect(jsonPath("$.amenities", org.hamcrest.Matchers.hasItem("조식")))
        .andExpect(jsonPath("$.maxGuests").value(4))
        .andExpect(jsonPath("$.checkInTime").value("15:00"))
        .andExpect(jsonPath("$.checkOutTime").value("11:00"))
        .andExpect(jsonPath("$.reviewCount").value(0))
        // #43 V16 — sido/gugun 응답 노출
        .andExpect(jsonPath("$.sido").value("서울특별시"))
        .andExpect(jsonPath("$.gugun").value("중구"));
  }

  @Test
  @DisplayName("GET /api/accommodations/{id} — 존재하지 않는 id면 404")
  void getById_notFound() throws Exception {
    mockMvc.perform(get("/api/accommodations/{id}", 999_999L)).andExpect(status().isNotFound());
  }
}

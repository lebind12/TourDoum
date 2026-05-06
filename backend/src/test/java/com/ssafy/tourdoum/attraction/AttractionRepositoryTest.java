package com.ssafy.tourdoum.attraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.tourdoum.global.JpaAuditingConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * AttractionRepository 슬라이스 테스트.
 *
 * <p>H2 인메모리 DB 사용 (test/resources/application.yml 참조). ST_Distance_Sphere는 H2 미지원이므로 공간
 * 검색(findNearby)은 검증하지 않는다. 공간 검색 검증은 후속 통합 테스트(MySQL Testcontainers)에서 수행한다.
 *
 * <p>@DataJpaTest는 @EnableJpaAuditing을 로드하지 않으므로 JpaAuditingConfig를 명시적으로 import해야 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class AttractionRepositoryTest {

  @Autowired private AttractionRepository attractionRepository;

  private Attraction buildAttraction(String name, String region, AttractionCategory category) {
    return Attraction.builder()
        .name(name)
        .region(region)
        .category(category)
        .address("테스트 주소")
        .latitude(new BigDecimal("37.579617"))
        .longitude(new BigDecimal("126.977041"))
        .description("테스트 설명")
        .build();
  }

  @Test
  @DisplayName("저장 후 단건 조회 성공")
  void saveAndFindById() {
    // given
    Attraction attraction = buildAttraction("경복궁", "서울", AttractionCategory.HISTORY);

    // when
    Attraction saved = attractionRepository.save(attraction);
    Optional<Attraction> found = attractionRepository.findById(saved.getId());

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("경복궁");
    assertThat(found.get().getRegion()).isEqualTo("서울");
    assertThat(found.get().getCategory()).isEqualTo(AttractionCategory.HISTORY);
  }

  @Test
  @DisplayName("삭제 후 조회 시 빈 Optional 반환")
  void deleteAndFindById() {
    // given
    Attraction saved =
        attractionRepository.save(buildAttraction("삭제테스트", "경기", AttractionCategory.NATURE));

    // when
    attractionRepository.deleteById(saved.getId());
    Optional<Attraction> found = attractionRepository.findById(saved.getId());

    // then
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("지역으로 필터링 — region 일치 항목만 반환")
  void findByRegion() {
    // given
    attractionRepository.save(buildAttraction("북촌한옥마을", "서울", AttractionCategory.HISTORY));
    attractionRepository.save(buildAttraction("인사동", "서울", AttractionCategory.SHOPPING));
    attractionRepository.save(buildAttraction("수원화성", "경기", AttractionCategory.HISTORY));

    // when
    Page<Attraction> result = attractionRepository.findByRegion("서울", PageRequest.of(0, 10));

    // then
    assertThat(result.getTotalElements()).isEqualTo(2);
    List<String> names = result.getContent().stream().map(Attraction::getName).toList();
    assertThat(names).containsExactlyInAnyOrder("북촌한옥마을", "인사동");
  }

  @Test
  @DisplayName("카테고리로 필터링 — category 일치 항목만 반환")
  void findByCategory() {
    // given
    attractionRepository.save(buildAttraction("경복궁", "서울", AttractionCategory.HISTORY));
    attractionRepository.save(buildAttraction("창덕궁", "서울", AttractionCategory.HISTORY));
    attractionRepository.save(buildAttraction("설악산", "강원", AttractionCategory.NATURE));

    // when
    Page<Attraction> result =
        attractionRepository.findByCategory(AttractionCategory.HISTORY, PageRequest.of(0, 10));

    // then
    assertThat(result.getTotalElements()).isEqualTo(2);
    result
        .getContent()
        .forEach(a -> assertThat(a.getCategory()).isEqualTo(AttractionCategory.HISTORY));
  }

  // 공간 검색(findNearby) 테스트는 여기서 작성하지 않는다.
  // H2가 ST_Distance_Sphere를 지원하지 않아 UnsupportedOperationException 발생.
  // 후속 worktree에서 MySQL Testcontainers 기반 통합 테스트로 작성할 것.
}

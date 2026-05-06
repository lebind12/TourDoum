// 학습 친화 모드: 신규 테스트는 사용자가 작성. 본 파일은 패턴 참고용.
package com.ssafy.tourdoum.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * JPA 슬라이스 테스트 — 빈 깡통 확인용.
 *
 * <p>{@link DataJpaTest}는 인메모리 H2 또는 Testcontainers 없이도 JPA 컨텍스트를 로드한다.
 *
 * <p>새 Entity 추가 시 예시:
 *
 * <pre>{@code
 * @Test
 * void saveAndFind_attraction() {
 *   Attraction a = new Attraction("경복궁", 37.579, 126.977);
 *   em.persist(a);
 *   em.flush();
 *   em.clear();
 *   Attraction found = em.find(Attraction.class, a.getId());
 *   assertThat(found.getName()).isEqualTo("경복궁");
 * }
 * }</pre>
 */
@DataJpaTest
class JpaSliceSampleTest {

  @PersistenceContext private EntityManager em;

  @Test
  @DisplayName("EntityManager 가 정상 주입된다")
  void entityManagerIsInjected() {
    // 여기에 entity 추가 시 테스트 작성 예시 — 위의 Javadoc 참고
    assertThat(em).isNotNull();
  }
}

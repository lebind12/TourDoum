// 학습 친화 모드: 신규 테스트는 사용자가 작성. 본 파일은 패턴 참고용.
package com.ssafy.tourdoum.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HealthController 단위 테스트.
 *
 * <p>{@link WebMvcTest}는 Web 레이어만 로딩하므로 DB/Redis 연결 없이 실행된다.
 *
 * <p>Security 필터는 컨트롤러 단위 테스트 목적에 맞지 않으므로 비활성화 (addFilters=false). /api/health 는 인증 불필요 경로이므로 통합
 * 테스트(AuthIntegrationTest)에서 검증.
 */
@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("GET /api/health 는 200 OK 와 {status:UP, app:tourdoum} 를 반환한다")
  void health_returns200AndBody() throws Exception {
    mockMvc
        .perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.app").value("tourdoum"));
  }
}

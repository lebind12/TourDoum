package com.ssafy.tourdoum.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습용 커스텀 헬스체크 엔드포인트.
 *
 * <p>Spring Actuator의 {@code /actuator/health} 와 별개로 동작한다. 도메인 상태 확인, 간단한 응답 포맷 학습에 활용.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "app", "tourdoum");
  }
}

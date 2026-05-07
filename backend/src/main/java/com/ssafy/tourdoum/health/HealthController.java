package com.ssafy.tourdoum.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습용 커스텀 헬스체크 엔드포인트.
 *
 * <p>Spring Actuator의 {@code /actuator/health} 와 별개로 동작한다. 도메인 상태 확인, 간단한 응답 포맷 학습에 활용.
 */
@Tag(name = "Health", description = "애플리케이션 헬스체크 (커스텀)")
@RestController
@RequestMapping("/api")
public class HealthController {

  @Operation(
      summary = "헬스체크",
      description = "애플리케이션이 정상 동작 중인지 확인한다. 인증 불필요. {\"status\":\"UP\",\"app\":\"tourdoum\"} 반환.")
  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "app", "tourdoum");
  }
}

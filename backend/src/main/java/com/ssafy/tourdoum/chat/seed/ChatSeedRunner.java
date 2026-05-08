package com.ssafy.tourdoum.chat.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * dev-only seed entrypoint. profile {@code chat-seed} 미활성 시 빈 등록 안 됨 — production 가드.
 *
 * <p>사용:
 *
 * <pre>
 * SPRING_PROFILES_ACTIVE=chat-seed ./mvnw spring-boot:run \
 *   -Dspring-boot.run.arguments="--scenario=mixed --rows=10000 --resume=true"
 * </pre>
 *
 * <ul>
 *   <li>{@code --scenario=public|dm|mixed} (default mixed)
 *   <li>{@code --rows=N} (default ADR per-scenario; IT는 1만)
 *   <li>{@code --resume=true|false} (default true) — sentinel hit 시 skip
 * </ul>
 *
 * <p>실행 후 {@link SpringApplication#exit}로 종료. 머지된 후 architect/사용자가 직접 활성화해서 박는다.
 */
@Component
@Profile("chat-seed")
public class ChatSeedRunner implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(ChatSeedRunner.class);

  private final ChatSeedingService service;
  private final ApplicationContext context;

  public ChatSeedRunner(ChatSeedingService service, ApplicationContext context) {
    this.service = service;
    this.context = context;
  }

  @Override
  public void run(String... args) {
    ChatSeedScenario scenario = ChatSeedScenario.MIXED;
    Long rows = null;
    boolean resume = true;

    for (String a : args) {
      if (a.startsWith("--scenario=")) {
        scenario = ChatSeedScenario.parse(a.substring("--scenario=".length()));
      } else if (a.startsWith("--rows=")) {
        rows = Long.parseLong(a.substring("--rows=".length()).trim());
      } else if (a.startsWith("--resume=")) {
        resume = Boolean.parseBoolean(a.substring("--resume=".length()).trim());
      }
    }

    LOG.info("=== ChatSeedRunner START scenario={} rows={} resume={} ===", scenario, rows, resume);
    SeedReport report = service.seed(scenario, rows, resume);
    LOG.info("=== ChatSeedRunner DONE: {} ===", report);

    int exit = SpringApplication.exit(context, () -> 0);
    if (exit != 0) {
      System.exit(exit);
    }
  }
}

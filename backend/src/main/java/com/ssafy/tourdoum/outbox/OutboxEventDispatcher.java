package com.ssafy.tourdoum.outbox;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * event_type → {@link OutboxEventHandler} 라우팅. Spring이 모든 {@link OutboxEventHandler} 빈을
 * 자동 수집해 본 dispatcher가 type별 map을 build.
 *
 * <p>handler가 등록되지 않은 type은 publisher가 dead_letter 즉시 이관 (orphan event).
 */
@Component
public class OutboxEventDispatcher {

  private final Map<String, OutboxEventHandler> handlers;

  public OutboxEventDispatcher(List<OutboxEventHandler> handlers) {
    this.handlers =
        handlers.stream()
            .collect(
                Collectors.toMap(
                    OutboxEventHandler::eventType,
                    Function.identity(),
                    (a, b) -> {
                      throw new IllegalStateException(
                          "동일 eventType handler 중복 등록: " + a.eventType());
                    }));
  }

  public Optional<OutboxEventHandler> handlerFor(String eventType) {
    return Optional.ofNullable(handlers.get(eventType));
  }
}

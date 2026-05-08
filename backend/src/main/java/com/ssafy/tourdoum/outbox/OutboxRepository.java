package com.ssafy.tourdoum.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

  List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(Long aggregateId);

  List<OutboxEvent> findByAggregateIdAndEventType(Long aggregateId, String eventType);
}

package com.ssafy.tourdoum.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** OutboxPublisher 단위 테스트 — Mock repo + handler. ADR-0013 §결정 (11) 5요소 검증. */
class OutboxPublisherTest {

  private final OutboxRepository repo = org.mockito.Mockito.mock(OutboxRepository.class);
  private final OutboxDeadLetterRepository dlq =
      org.mockito.Mockito.mock(OutboxDeadLetterRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);

  private OutboxEvent stub(Long id, String type, int attempt) {
    OutboxEvent e =
        OutboxEvent.builder()
            .aggregateId(11L)
            .eventType(type)
            .payload("{}")
            .availableAt(LocalDateTime.now(clock))
            .build();
    org.springframework.test.util.ReflectionTestUtils.setField(e, "id", id);
    org.springframework.test.util.ReflectionTestUtils.setField(e, "attemptCount", attempt);
    return e;
  }

  private OutboxPublisher newPublisher(OutboxEventDispatcher dispatcher) {
    // Test-only TransactionTemplate — execute callback inline (no real tx).
    org.springframework.transaction.support.TransactionTemplate tt =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
              @Override
              protected Object doGetTransaction() { return new Object(); }
              @Override
              protected void doBegin(Object t, org.springframework.transaction.TransactionDefinition d) {}
              @Override
              protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus s) {}
              @Override
              protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus s) {}
            });
    return new OutboxPublisher(repo, dlq, dispatcher, tt, 50, 5, Duration.ofSeconds(30), clock);
  }

  @Test
  @DisplayName("processOne 성공 → markDone 호출, dead_letter 미사용")
  void process_success() {
    OutboxEvent ev = stub(7L, "Notify", 0);
    given(repo.findById(7L)).willReturn(Optional.of(ev));
    OutboxEventHandler h = org.mockito.Mockito.mock(OutboxEventHandler.class);
    given(h.eventType()).willReturn("Notify");
    OutboxEventDispatcher d = new OutboxEventDispatcher(List.of(h));

    newPublisher(d).processOne(7L);

    verify(h).handle(ev);
    verify(repo).markDone(eq(7L), any());
    verify(dlq, never()).save(any());
    verify(repo, never()).markBackoff(anyLong(), any());
  }

  @Test
  @DisplayName("processOne 실패 + attempt < max → backoff (다음 attempt에 2^n s 연기)")
  void process_failure_backoff() {
    OutboxEvent ev = stub(8L, "Notify", 1);
    given(repo.findById(8L)).willReturn(Optional.of(ev));
    OutboxEventHandler h = org.mockito.Mockito.mock(OutboxEventHandler.class);
    given(h.eventType()).willReturn("Notify");
    org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(h).handle(any());
    OutboxEventDispatcher d = new OutboxEventDispatcher(List.of(h));

    newPublisher(d).processOne(8L);

    LocalDateTime expected = LocalDateTime.now(clock).plusSeconds(OutboxPublisher.backoffSeconds(2));
    verify(repo).markBackoff(8L, expected);
    verify(dlq, never()).save(any());
    verify(repo, never()).markDone(anyLong(), any());
  }

  @Test
  @DisplayName("processOne 실패 + attempt >= max → dead_letter 이관 + markFailedTerminal")
  void process_failure_dead_letter() {
    OutboxEvent ev = stub(9L, "Notify", 4); // 다음 attempt 5 == max
    given(repo.findById(9L)).willReturn(Optional.of(ev));
    OutboxEventHandler h = org.mockito.Mockito.mock(OutboxEventHandler.class);
    given(h.eventType()).willReturn("Notify");
    org.mockito.Mockito.doThrow(new RuntimeException("perm fail")).when(h).handle(any());
    OutboxEventDispatcher d = new OutboxEventDispatcher(List.of(h));

    newPublisher(d).processOne(9L);

    verify(dlq).save(any(OutboxDeadLetter.class));
    verify(repo).markFailedTerminal(9L);
    verify(repo, never()).markBackoff(anyLong(), any());
  }

  @Test
  @DisplayName("orphan eventType (handler 미등록) → 즉시 dead_letter")
  void process_orphan_event_to_dead_letter() {
    OutboxEvent ev = stub(10L, "UnknownType", 0);
    given(repo.findById(10L)).willReturn(Optional.of(ev));
    OutboxEventDispatcher d = new OutboxEventDispatcher(List.of()); // 비어 있음

    newPublisher(d).processOne(10L);

    verify(dlq).save(any(OutboxDeadLetter.class));
    verify(repo).markFailedTerminal(10L);
  }

  @Test
  @DisplayName("recoverStale → claimedAt < now-TTL row 복원 호출")
  void recoverStale_calls_repo() {
    OutboxEventDispatcher d = new OutboxEventDispatcher(List.of());
    newPublisher(d).recoverStale();
    LocalDateTime threshold = LocalDateTime.now(clock).minus(Duration.ofSeconds(30));
    verify(repo).recoverStaleClaims(threshold);
  }

  @Test
  @DisplayName("backoffSeconds — 2^attempt (cap 10)")
  void backoff_curve() {
    assertThat(OutboxPublisher.backoffSeconds(1)).isEqualTo(2);
    assertThat(OutboxPublisher.backoffSeconds(2)).isEqualTo(4);
    assertThat(OutboxPublisher.backoffSeconds(3)).isEqualTo(8);
    assertThat(OutboxPublisher.backoffSeconds(4)).isEqualTo(16);
    assertThat(OutboxPublisher.backoffSeconds(5)).isEqualTo(32);
    // cap
    assertThat(OutboxPublisher.backoffSeconds(20)).isEqualTo(1L << 10);
  }
}

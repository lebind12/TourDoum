package com.ssafy.tourdoum.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxDeadLetterRepository extends JpaRepository<OutboxDeadLetter, Long> {}

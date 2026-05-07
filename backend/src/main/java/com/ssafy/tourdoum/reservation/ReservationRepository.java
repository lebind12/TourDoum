package com.ssafy.tourdoum.reservation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 예약 레포지토리. */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

  List<Reservation> findByMemberIdOrderByCreatedAtDesc(Long memberId);

  Optional<Reservation> findByIdempotencyKey(String idempotencyKey);
}

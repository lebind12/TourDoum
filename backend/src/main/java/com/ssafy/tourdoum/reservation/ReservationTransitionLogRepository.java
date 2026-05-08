package com.ssafy.tourdoum.reservation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationTransitionLogRepository
    extends JpaRepository<ReservationTransitionLog, Long> {

  List<ReservationTransitionLog> findByReservationIdOrderByCreatedAtAsc(Long reservationId);
}

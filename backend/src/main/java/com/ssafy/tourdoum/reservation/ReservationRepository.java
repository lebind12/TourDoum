package com.ssafy.tourdoum.reservation;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 예약 레포지토리. */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

  List<Reservation> findByMemberIdOrderByCreatedAtDesc(Long memberId);

  Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

  /**
   * FSM conditional UPDATE — ADR-0013 §결정 (3) "분산 안전 코드 패턴". expectedPrev에 매치되지 않으면 0 row 갱신
   * (다른 replica가 이미 처리한 멱등 no-op).
   *
   * @return 영향받은 row 수 (0 또는 1)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE Reservation r
         SET r.state = :newState,
             r.updatedAt = :now
       WHERE r.id = :id
         AND r.state IN :expectedPrev
      """)
  int transitionState(
      @Param("id") Long id,
      @Param("newState") ReservationState newState,
      @Param("expectedPrev") Collection<ReservationState> expectedPrev,
      @Param("now") LocalDateTime now);
}

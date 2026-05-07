package com.ssafy.tourdoum.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 알림 JPA 레포지토리. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

  /** 회원 알림 목록 (최신순 페이징). */
  Page<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);

  /** 미읽음 알림 수. */
  long countByMemberIdAndReadAtIsNull(Long memberId);

  /** 회원의 미읽음 알림 전체 읽음 처리. */
  @Modifying
  @Query(
      "UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP"
          + " WHERE n.memberId = :memberId AND n.readAt IS NULL")
  int markAllReadByMemberId(@Param("memberId") Long memberId);
}

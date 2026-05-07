package com.ssafy.tourdoum.notification;

import com.ssafy.tourdoum.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 서비스. publish()는 ReviewService / ReservationService에서 내부 호출. */
@Service
@Transactional(readOnly = true)
public class NotificationService {

  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  /**
   * 알림 목록 조회 (최신순 페이징).
   *
   * @param memberId 로그인 회원 PK
   * @param pageable 페이징 파라미터
   */
  public PageResponse<NotificationResponse> list(Long memberId, Pageable pageable) {
    return PageResponse.from(
        notificationRepository
            .findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
            .map(NotificationResponse::from));
  }

  /**
   * 미읽음 알림 수.
   *
   * @param memberId 로그인 회원 PK
   */
  public UnreadCountResponse unreadCount(Long memberId) {
    return new UnreadCountResponse(notificationRepository.countByMemberIdAndReadAtIsNull(memberId));
  }

  /**
   * 단건 읽음 처리.
   *
   * @param notificationId 알림 PK
   * @param memberId 로그인 회원 PK (권한 체크)
   * @throws NotificationNotFoundException 알림 미존재 (→ 404)
   * @throws NotificationForbiddenException 본인 알림 아님 (→ 403)
   */
  @Transactional
  public NotificationResponse markRead(Long notificationId, Long memberId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    if (!notification.getMemberId().equals(memberId)) {
      throw new NotificationForbiddenException(notificationId);
    }

    notification.markRead();
    return NotificationResponse.from(notification);
  }

  /**
   * 전체 읽음 처리.
   *
   * @param memberId 로그인 회원 PK
   * @return 갱신된 알림 수
   */
  @Transactional
  public int markAllRead(Long memberId) {
    return notificationRepository.markAllReadByMemberId(memberId);
  }

  /**
   * 알림 발행 (내부 호출용).
   *
   * <p>ReviewService.create() 및 ReservationService.confirm() 에서 호출. 트랜잭션 경계는 호출자에 위임 (REQUIRED
   * 기본값으로 합류).
   *
   * @param memberId 수신 회원 PK
   * @param type 알림 유형
   * @param title 알림 제목
   * @param body 알림 본문
   * @param linkUrl 연결 URL (nullable)
   */
  @Transactional
  public void publish(
      Long memberId, NotificationType type, String title, String body, String linkUrl) {
    notificationRepository.save(
        Notification.builder()
            .memberId(memberId)
            .type(type)
            .title(title)
            .body(body)
            .linkUrl(linkUrl)
            .build());
  }
}

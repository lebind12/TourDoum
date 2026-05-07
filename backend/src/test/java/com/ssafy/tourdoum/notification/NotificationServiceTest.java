package com.ssafy.tourdoum.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** NotificationService 단위 테스트 (Mockito). 학습 친화 모드 — 양산 금지. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationRepository notificationRepository;

  @InjectMocks private NotificationService notificationService;

  private Notification buildNotification(Long memberId) {
    return Notification.builder()
        .memberId(memberId)
        .type(NotificationType.RESERVATION_CONFIRMED)
        .title("예약이 확정되었습니다")
        .body("테스트 호텔 예약이 성공적으로 확정되었습니다.")
        .linkUrl("/reservations/me")
        .build();
  }

  @Test
  @DisplayName("markRead — 본인 알림이 아닌 경우 NotificationForbiddenException 발생")
  void markRead_forbiddenForOtherMember() {
    // given
    Long notificationId = 1L;
    Long ownerMemberId = 10L;
    Long otherMemberId = 99L;

    Notification notification = buildNotification(ownerMemberId);
    given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

    // when / then
    assertThatThrownBy(() -> notificationService.markRead(notificationId, otherMemberId))
        .isInstanceOf(NotificationForbiddenException.class)
        .hasMessageContaining("id=" + notificationId);
  }

  @Test
  @DisplayName("markRead — 본인 알림이면 readAt이 설정됨")
  void markRead_ownerCanRead() {
    // given
    Long notificationId = 1L;
    Long memberId = 10L;

    Notification notification = buildNotification(memberId);
    assertThat(notification.isUnread()).isTrue();

    given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

    // when
    NotificationResponse response = notificationService.markRead(notificationId, memberId);

    // then
    assertThat(response.unread()).isFalse();
  }
}

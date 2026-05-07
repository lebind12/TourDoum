package com.ssafy.tourdoum.notification;

import com.ssafy.tourdoum.attraction.PageResponse;
import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 REST 컨트롤러.
 *
 * <p>폴링 기반. 전 엔드포인트 SESSION 인증 필수.
 */
@Tag(name = "Notification", description = "알림 조회 / 읽음 처리 API (폴링 기반)")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationService notificationService;
  private final MemberRepository memberRepository;

  public NotificationController(
      NotificationService notificationService, MemberRepository memberRepository) {
    this.notificationService = notificationService;
    this.memberRepository = memberRepository;
  }

  /** 알림 목록 (최신순 페이징). */
  @Operation(summary = "알림 목록", description = "로그인 회원의 알림 목록을 최신순으로 반환한다. page/size 쿼리 파라미터 지원.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "알림 목록"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @SecurityRequirement(name = "SESSION")
  @GetMapping
  public ResponseEntity<PageResponse<NotificationResponse>> list(
      @Parameter(description = "페이지 번호 (0-based)") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(notificationService.list(memberId, pageable));
  }

  /** 미읽음 알림 수. */
  @Operation(summary = "미읽음 알림 수", description = "폴링 시 주기적으로 호출해 미읽음 배지 수를 갱신한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "미읽음 수"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @SecurityRequirement(name = "SESSION")
  @GetMapping("/unread-count")
  public ResponseEntity<UnreadCountResponse> unreadCount(
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(notificationService.unreadCount(memberId));
  }

  /** 단건 읽음 처리. */
  @Operation(summary = "단건 읽음 처리", description = "알림 ID를 지정해 읽음 처리한다. 본인 알림만 처리 가능.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "읽음 처리된 알림"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "403", description = "권한 없음"),
    @ApiResponse(responseCode = "404", description = "알림 미존재")
  })
  @SecurityRequirement(name = "SESSION")
  @PostMapping("/{id}/read")
  public ResponseEntity<NotificationResponse> markRead(
      @Parameter(description = "알림 PK") @PathVariable Long id,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(notificationService.markRead(id, memberId));
  }

  /** 전체 읽음 처리. */
  @Operation(summary = "전체 읽음 처리", description = "미읽음 알림 전체를 읽음 처리한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "갱신된 알림 수"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @SecurityRequirement(name = "SESSION")
  @PostMapping("/read-all")
  public ResponseEntity<UnreadCountResponse> markAllRead(
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    int updated = notificationService.markAllRead(memberId);
    return ResponseEntity.ok(new UnreadCountResponse(updated));
  }

  private Long resolveMemberId(UserDetails userDetails) {
    Member member =
        memberRepository
            .findByEmail(userDetails.getUsername())
            .orElseThrow(
                () -> new UsernameNotFoundException("회원 없음: " + userDetails.getUsername()));
    return member.getId();
  }
}

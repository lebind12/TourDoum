package com.ssafy.tourdoum.chat;

import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채팅 REST 컨트롤러 — 폴링 + keyset paging (ADR-0012 v2 BE-1).
 *
 * <p>인증: ADR-0011 BE-3 — Bearer access + cookie refresh.
 */
@Tag(name = "Chat", description = "채팅 채널/메시지 API (폴링 + keyset paging, WebSocket 미사용)")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

  private final ChatService chatService;
  private final MemberRepository memberRepository;

  public ChatController(ChatService chatService, MemberRepository memberRepository) {
    this.chatService = chatService;
    this.memberRepository = memberRepository;
  }

  /** 내 채널 목록. */
  @Operation(summary = "내 채널 목록", description = "로그인 회원이 참여 중인 채팅 채널 목록을 반환한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "채널 목록"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/channels")
  public ResponseEntity<List<ChatChannelResponse>> listMyChannels(
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(chatService.listMyChannels(memberId));
  }

  /**
   * 채널 메시지 조회 — 신규 `?afterCursor=` (forward polling, ADR-0012 v2) + 한시 호환 `?sinceId=`.
   *
   * <p>afterCursor가 있으면 우선. 둘 다 없으면 최신 limit건 ASC 반환.
   */
  @Operation(
      summary = "채널 메시지 조회 (forward / 초기 로드)",
      description =
          "afterCursor가 있으면 forward polling — cursor 이후 신규 메시지 ASC. 둘 다 없으면 최신 limit건 ASC. sinceId는 한시 호환.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "메시지 페이지"),
    @ApiResponse(responseCode = "400", description = "cursor 디코드 실패"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "403", description = "채널 구성원 아님"),
    @ApiResponse(responseCode = "404", description = "채널 미존재")
  })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/channels/{id}/messages")
  public ResponseEntity<ChatMessagePage> messages(
      @Parameter(description = "채널 PK") @PathVariable Long id,
      @Parameter(description = "forward cursor — base64url(JSON {t,id})")
          @RequestParam(required = false)
          String afterCursor,
      @Parameter(description = "legacy 마지막 메시지 ID (afterCursor가 있으면 무시, 0=초기)")
          @RequestParam(defaultValue = "0")
          long sinceId,
      @Parameter(description = "1≤limit≤50, default 20") @RequestParam(required = false)
          Integer limit,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(chatService.messages(id, memberId, afterCursor, sinceId, limit));
  }

  /** 채널 메시지 — older(backward, 스크롤 업) keyset paging. ADR-0012 v2 신규. */
  @Operation(
      summary = "채널 메시지 older (backward)",
      description =
          "beforeCursor 미만의 메시지를 ChronologicalASC로 반환. cursor 미지정 시 최신 limit건. nextCursor가 null이면 더 이상 없음.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "메시지 페이지"),
    @ApiResponse(responseCode = "400", description = "cursor 디코드 실패"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "403", description = "채널 구성원 아님"),
    @ApiResponse(responseCode = "404", description = "채널 미존재")
  })
  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/channels/{id}/messages/older")
  public ResponseEntity<ChatMessagePage> messagesOlder(
      @Parameter(description = "채널 PK") @PathVariable Long id,
      @Parameter(description = "backward cursor — base64url(JSON {t,id})")
          @RequestParam(required = false)
          String beforeCursor,
      @Parameter(description = "1≤limit≤50, default 20") @RequestParam(required = false)
          Integer limit,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(chatService.messagesOlder(id, memberId, beforeCursor, limit));
  }

  /** 메시지 전송. */
  @Operation(summary = "메시지 전송", description = "채널에 메시지를 전송한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "전송된 메시지"),
    @ApiResponse(responseCode = "400", description = "잘못된 파라미터"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "403", description = "채널 구성원 아님"),
    @ApiResponse(responseCode = "404", description = "채널 미존재")
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/channels/{id}/messages")
  public ResponseEntity<ChatMessageResponse> send(
      @Parameter(description = "채널 PK") @PathVariable Long id,
      @Valid @RequestBody ChatSendRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(chatService.send(id, memberId, request.content()));
  }

  /** DM 채널 열기 (없으면 자동 생성). */
  @Operation(summary = "DM 채널 열기", description = "상대방 회원 ID로 DM 채널을 열거나 기존 채널을 반환한다. 중복 생성 없음.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "DM 채널 (기존 또는 신규)"),
    @ApiResponse(responseCode = "400", description = "자기 자신과 DM 불가"),
    @ApiResponse(responseCode = "401", description = "인증 필요")
  })
  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/dm")
  public ResponseEntity<ChatChannelResponse> openDm(
      @Valid @RequestBody OpenDmRequest request, @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(chatService.openDm(memberId, request.otherMemberId()));
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

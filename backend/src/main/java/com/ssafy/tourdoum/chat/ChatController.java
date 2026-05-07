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
 * 채팅 REST 컨트롤러.
 *
 * <p>폴링 기반. 전 엔드포인트 SESSION 인증 필수.
 */
@Tag(name = "Chat", description = "채팅 채널/메시지 API (폴링 기반, WebSocket 미사용)")
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
  @SecurityRequirement(name = "SESSION")
  @GetMapping("/channels")
  public ResponseEntity<List<ChatChannelResponse>> listMyChannels(
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(chatService.listMyChannels(memberId));
  }

  /** 채널 메시지 조회 (폴링). */
  @Operation(
      summary = "채널 메시지 조회",
      description = "sinceId 이후의 신규 메시지를 반환한다. sinceId=0이면 최근 50건 반환. 1-3초 주기 폴링 용도.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "메시지 목록"),
    @ApiResponse(responseCode = "401", description = "인증 필요"),
    @ApiResponse(responseCode = "403", description = "채널 구성원 아님"),
    @ApiResponse(responseCode = "404", description = "채널 미존재")
  })
  @SecurityRequirement(name = "SESSION")
  @GetMapping("/channels/{id}/messages")
  public ResponseEntity<List<ChatMessageResponse>> messages(
      @Parameter(description = "채널 PK") @PathVariable Long id,
      @Parameter(description = "마지막 메시지 ID (0=전체)") @RequestParam(defaultValue = "0") long sinceId,
      @AuthenticationPrincipal UserDetails userDetails) {
    Long memberId = resolveMemberId(userDetails);
    return ResponseEntity.ok(chatService.messages(id, memberId, sinceId));
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
  @SecurityRequirement(name = "SESSION")
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
  @SecurityRequirement(name = "SESSION")
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

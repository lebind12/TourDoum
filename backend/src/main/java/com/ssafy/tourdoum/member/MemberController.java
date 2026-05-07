package com.ssafy.tourdoum.member;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 회원 API. */
@Tag(name = "Member", description = "회원 가입·관리 API")
@RestController
@RequestMapping("/api/members")
public class MemberController {

  private final MemberService memberService;

  public MemberController(MemberService memberService) {
    this.memberService = memberService;
  }

  /** POST /api/members/signup — 회원가입. 201 Created + {id, email, nickname}. */
  @Operation(summary = "회원가입", description = "이메일·비밀번호·닉네임으로 신규 계정을 생성한다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "회원가입 성공"),
    @ApiResponse(responseCode = "400", description = "입력값 유효성 오류 (이메일 형식, 비밀번호 길이 등)"),
    @ApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복")
  })
  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
    Member member = memberService.signup(request);
    return SignupResponse.from(member);
  }
}

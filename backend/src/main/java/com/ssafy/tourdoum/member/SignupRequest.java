package com.ssafy.tourdoum.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회원가입 요청 DTO. */
public record SignupRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 12, max = 64, message = "비밀번호는 12자 이상 64자 이하이어야 합니다.")
        String password,
    @NotBlank @Size(min = 2, max = 50, message = "닉네임은 2~50자이어야 합니다.") String nickname) {}

package com.ssafy.tourdoum.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reset 토큰 사용 + 새 비밀번호 — ADR-0011 BE-4.5. */
public record PasswordResetCompleteRequest(
    @NotBlank String token,
    @NotBlank @Size(min = PasswordPolicyValidator.MIN_LENGTH, max = PasswordPolicyValidator.MAX_LENGTH)
        String newPassword) {}

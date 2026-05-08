package com.ssafy.tourdoum.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청 — ADR-0011 BE-4.3.
 *
 * <p>구체 정책(공통 패스워드, blocklist 등)은 {@link PasswordPolicyValidator}가 검증.
 * 여기 {@code @Size}는 1차 빠른 reject만 담당한다.
 */
public record PasswordChangeRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = PasswordPolicyValidator.MIN_LENGTH, max = PasswordPolicyValidator.MAX_LENGTH)
        String newPassword) {}

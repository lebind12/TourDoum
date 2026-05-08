package com.ssafy.tourdoum.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Reset 토큰 발급 요청 — ADR-0011 BE-4.5. */
public record PasswordResetInitiateRequest(@NotBlank @Email String email) {}

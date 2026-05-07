package com.ssafy.tourdoum.auth;

import jakarta.validation.constraints.NotBlank;

/** `POST /api/auth/refresh` 요청 body — refresh token (cookie 흐름은 BE-3에서 추가). */
public record RefreshRequest(@NotBlank String refreshToken) {}

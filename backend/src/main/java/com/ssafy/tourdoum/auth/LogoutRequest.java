package com.ssafy.tourdoum.auth;

/**
 * `POST /api/auth/logout` 요청 body — refresh token 동봉(family 폐기 대상). null/빈 값이면 access denylist만 처리.
 */
public record LogoutRequest(String refreshToken) {}

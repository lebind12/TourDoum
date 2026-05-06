package com.ssafy.tourdoum.auth;

/** 로그인 요청 JSON body DTO. */
public record LoginRequest(String email, String password) {}

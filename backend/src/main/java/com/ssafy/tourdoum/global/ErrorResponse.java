package com.ssafy.tourdoum.global;

/** 검증 오류 단건 응답 DTO. */
public record ErrorResponse(String field, String message) {}

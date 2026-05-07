package com.ssafy.tourdoum.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 메시지 전송 요청 — content @Size(max=4000) per ADR-0012 v2 §3. */
public record ChatSendRequest(@NotBlank @Size(max = 4000) String content) {}

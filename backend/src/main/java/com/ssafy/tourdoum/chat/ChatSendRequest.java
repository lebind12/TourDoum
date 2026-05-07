package com.ssafy.tourdoum.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 메시지 전송 요청. */
public record ChatSendRequest(@NotBlank @Size(max = 2000) String content) {}

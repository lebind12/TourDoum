package com.ssafy.tourdoum.chat;

import jakarta.validation.constraints.NotNull;

/** DM 채널 열기 요청. */
public record OpenDmRequest(@NotNull Long otherMemberId) {}

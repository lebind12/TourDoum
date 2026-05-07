package com.ssafy.tourdoum.chat;

import java.util.List;

/**
 * 채팅 메시지 페이지 응답 — keyset paging (ADR-0012 v2 §3).
 *
 * <ul>
 *   <li>{@code items}: ChronologicalASC. older / newer / 초기 페이지 모두 ASC.
 *   <li>{@code nextCursor}: 다음 페이지 진입용. null이면 더 이상 없음(hasMore=false).
 *       <ul>
 *         <li>older(backward): 본 페이지 가장 오래된 메시지 cursor (FE는 다음 `beforeCursor`로 사용).
 *         <li>newer(forward): 본 페이지 가장 최신 메시지 cursor (FE는 다음 `afterCursor`로 사용).
 *       </ul>
 *   <li>{@code appliedLimit}: BE에서 clamp한 limit. FE 보정 가시화.
 *   <li>{@code hasMore}: items 크기 == appliedLimit 인지로 판정.
 * </ul>
 */
public record ChatMessagePage(
    List<ChatMessageResponse> items, String nextCursor, int appliedLimit, boolean hasMore) {}

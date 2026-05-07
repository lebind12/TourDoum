package com.ssafy.tourdoum.review;

/** 후기 집계 응답 DTO. */
public record ReviewSummaryResponse(double avgRating, long count) {}

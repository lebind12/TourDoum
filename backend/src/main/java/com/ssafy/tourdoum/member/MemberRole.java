package com.ssafy.tourdoum.member;

/** 회원 권한 역할. 단일 역할 시작 (ADR-0003). 관리자 등은 후속 ADR에서 확장. */
public enum MemberRole {
  ROLE_USER,
  ROLE_ADMIN
}

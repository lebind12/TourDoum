# ADR 0003 — 인증 방식 및 세션 전략

- 작성일: 2026-05-06
- 상태: **Superseded by [ADR-0011](0011-jwt-authentication.md)** (2026-05-07)
- 작성자: Implementer-A (BE) — be/feat-domain-user worktree

> **2026-05-07 갱신**: 본 ADR의 Spring Session + 폼 로그인 결정은 ADR-0011(JWT 단독 + httpOnly cookie + RS256 + refresh rotation + Redis revocation)로 대체. Redis 컨테이너는 유지하되 인증 저장소 역할은 제거되고 보안 상태 저장소(refresh family/denylist/throttling)로 재활용. 이력 보존을 위해 본 문서는 보존.

---

## 컨텍스트

TourDoum 백엔드에는 회원 인증이 필요하다. 스택 기준 ADR-0001에서 세션(Redis) 기반 폼 로그인을 1차 방식으로 결정했다. 소셜 로그인(카카오 OAuth2)은 학습 곡선 고려로 후속 ADR에 분리되었다.

Spring Boot 3.3 + Spring Security 6.x 환경에서 구체적인 인증 구현 방식을 결정한다:

- 클라이언트(Vue 3 SPA)가 JSON으로 로그인 요청을 전송한다.
- 서버는 세션을 Redis에 저장하고, 브라우저 쿠키로 세션 ID를 전달한다.
- 세션 저장소는 Spring Session Data Redis.

---

## 결정

| 항목 | 결정값 | 근거 |
|---|---|---|
| 인증 방식 | 폼 로그인 (`POST /api/auth/login` JSON body, `POST /api/auth/logout`) | 학습 곡선 최소화; JWT 비교 검토 후 기각 |
| 세션 저장소 | Redis (Spring Session Data Redis) | 서버 재시작 시 세션 유지, 수평 확장 대비 |
| 세션 직렬화 | **Jackson** (`GenericJackson2JsonRedisSerializer`) | JDK 직렬화는 클래스 시그니처 변경 시 역직렬화 실패; JSON은 가독성 + 클래스 정보(@class) 보존 |
| 비밀번호 해시 | BCrypt (`BCryptPasswordEncoder`) | Spring Security 기본값; adaptive cost, 레인보우 테이블 방어 |
| 세션 쿠키 | `SESSION`, HttpOnly, SameSite=Lax, Secure=false(dev)/true(prod) | HttpOnly: XSS 방어, SameSite=Lax: CSRF 완화 |
| 세션 만료 | 30분 idle (`spring.session.timeout=30m`) | 일반 서비스 표준 |
| 권한 모델 | 단일 역할 시작 — `ROLE_USER`, `ROLE_ADMIN` enum | 첫 구현 단순화; 세분 권한은 후속 ADR |
| 회원 식별자 | 이메일(unique) + 닉네임(unique) + 내부 PK(BIGINT auto) | 이메일 = 로그인 ID, 닉네임 = 표시명 |
| 소셜 로그인 | 후속 ADR (카카오 OAuth2) — 본 worktree 구현 제외 | 학습 단계적 도입 |

---

## 대안 검토 (요약)

### JWT (Stateless)

- 장점: 서버 무상태, 수평 확장 용이.
- 단점: 토큰 무효화 어려움(블랙리스트 필요), 로그아웃 구현 복잡, 리프레시 토큰 관리 부담.
- **기각**: TourDoum 초기 규모에서 Redis 세션 복잡도와 큰 차이 없음, 학습 친화성 고려.

### JDK 직렬화

- 기본값이지만 클래스 시그니처(`serialVersionUID`) 변경 시 역직렬화 실패.
- **기각**: Spring Security 업그레이드 등 라이브러리 변경 시 세션 무효화 위험.

### CSRF 활성화 (SynchronizerToken)

- SPA + 폼 로그인 조합에서 설정 복잡도 증가.
- SameSite=Lax가 대부분의 CSRF 시나리오를 차단.
- **dev 비활성**, 추후 prod 활성화 검토 (handoff.md 참고).

---

## 결과 (예상)

- **긍정**: 구현 단순, Spring Security 표준 학습 가능, Redis 세션 가시성(Redis CLI로 직접 확인).
- **부정**: 세션 만료 전략 추가 고려 필요(sliding vs fixed), prod 환경에서 CSRF/CORS 재검토 필요.
- **후속 작업**:
  - ADR-0004: DB 마이그레이션 도구 선택 (Flyway vs Liquibase)
  - 별도 ADR: 카카오 OAuth2 소셜 로그인
  - `handoff.md`: CSRF/CORS prod 활성화 TODO 명시

---

## 구현 위치

- `com.ssafy.tourdoum.auth.SecurityConfig` — Spring Security 6.x 설정
- `com.ssafy.tourdoum.auth.JsonAuthenticationFilter` — JSON body 로그인 필터
- `com.ssafy.tourdoum.auth.MemberDetailsService` — DB 기반 UserDetailsService
- `com.ssafy.tourdoum.global.RedisSessionConfig` — Jackson 직렬화 설정
- `application.yml`: `spring.session.timeout=30m`, `flush-mode=on_save`

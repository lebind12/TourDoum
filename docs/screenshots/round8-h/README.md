# Round 8 (H) — viewport < 640px screenshot 인덱스

본 회차에 Playwright MCP로 캡처한 **공개 라우트** 5종 × 3 viewport (320/375/640) 박제. 캡처 환경:
- vite dev `30173`, BE 미부팅(공개 라우트 한정 캡처).
- 다크/라이트 토글 미적용(라이트 모드 기준 — 다크 contrast는 R5 박제 후 별도 라운드).

## 캡처된 라우트 (14 파일)

| 라우트 | 320 | 375 | 640 | 메모 |
|---|---|---|---|---|
| `/` (랜딩) | ✓ | ✓ | ✓ | hero/grid 4단 → 1단 fallback OK |
| `/search` | ✓ | ✓ | ✓ | filter `sm:grid-cols-2` 동작 OK |
| `/accommodations` | ✓ | ✓ | ✓ | 카드 grid 모바일 OK, 필터바 hint 차후 점검 |
| `/login` | ✓ | ✓ | ✓ | `/chat` redirect 결과 페이지로도 사용 |
| `/dev/ui-catalog` | — | ✓ | ✓ | R7 Button 모션 + R8 Input/Select transition 확인용 |

## 미캡처 (BE/auth/seed 의존, 다음 라운드)

| 라우트 | 사유 | 제안 |
|---|---|---|
| `/me` | 401 → /login redirect. `/dev/me-mock`은 R7 박제했지만 child mount race로 본 회차도 redirect 발생 — **R9에서 setup-time 패치로 수정** |
| `/plans`, `/plans/new` | requiresAuth + plansStore.fetch | dev seed 라우트 또는 BE+seed 켠 상태에서 재캡처 |
| `/reservations/new/:id/dates` | requiresAuth + reservationStep | 동상 |
| `/chat`, `/chat/:id`, `/dm/:id` | 라우트 자체엔 requiresAuth 없으나 ChatView 내부에서 /login redirect — 별도 가드 분리 후 캡처 |

## 발견된 깨짐 (320px 기준)

- **없음 (양호)** — 본 회차 캡처 5 라우트 모두 320px에서도 컨텐츠가 화면에 들어맞음. hero 텍스트
  스케일(`text-4xl sm:text-5xl md:text-6xl`)이 320px에서도 가독.
- 다음 라운드(R9)에서 캡처 못 한 4 라우트 + 다크 모드 점검 필요.

## 재현

```bash
cd frontend
set -a; . .env.agent; set +a
npm run dev
# 브라우저: http://localhost:30173/
# DevTools → Toggle device toolbar → 320 / 375 / 640
```

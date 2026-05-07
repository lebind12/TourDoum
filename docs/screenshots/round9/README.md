# Round 9 — 폴리싱 라운드 검증 screenshot

| 파일 | 의도 |
|---|---|
| `me-mock-light-375.png` | R8 발견 race fix 검증 — `/dev/me-mock`이 /login redirect 없이 MeView 풀 렌더 |
| `alertdialog-open-375.png` | AlertDialog enter 후 정상 표시 (catalog 트리거 클릭) |

## 동적 검증(스크린샷에 안 잡힘 — evaluate로 측정)

### AlertDialog scale-in (정상)

전이 30ms 시점 측정:
```
transitionProperty:      transform, opacity
transitionDuration:      0.18s, 0.18s   (= --motion-base 180ms)
transitionTimingFunction: cubic-bezier(0.2, 0, 0, 1.2),   ← --ease-emphasized (transform)
                          cubic-bezier(0.2, 0, 0, 1)       ← --ease-standard  (opacity)
transform:               matrix(0.951, 0, 0, 0.951, 0, 0) ← scale 0.95 → 1 진행 중
opacity:                 0.022                              ← 0 → 1 진행 중
```

### reduced-motion 클램프 (정상)

`prefers-reduced-motion: reduce`를 시뮬레이션한 임시 스타일 주입 후 재오픈, 30ms 시점:
```
transitionDuration: 1e-05s           ← 글로벌 !important 클램프 적용
transform:          none             ← 즉시 최종 상태
opacity:            1
```

→ 본 회차 scale-in 추가는 reduced-motion 사용자에게도 회귀 0 (즉시 표시).

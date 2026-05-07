# docs/screenshots — 세션 로그용 캡처 보관

`session-log` 스킬(노션 페이지 작성 + cl-memory 양자화)이 본 디렉터리의 캡처를 첨부 이미지로 사용한다. 캡처는 두 경로로 들어옴:

## 1. Playwright e2e 자동 캡처

`scripts/e2e-auth-flow.sh` 실행 시 `docs/screenshots/<timestamp>/` 자동 생성·저장. 추가 옵션:

```bash
# 인터랙티브 UI 모드 (개발/디버깅 추천)
PLAYWRIGHT_ARGS=--ui scripts/e2e-auth-flow.sh

# 브라우저 창 보이기 (시각 확인)
PLAYWRIGHT_ARGS=--headed scripts/e2e-auth-flow.sh

# 별도 디렉터리 지정
CAPTURE_DIR=docs/screenshots/20260507-feat-x scripts/e2e-auth-flow.sh
```

또는 npm 스크립트:
```bash
cd frontend
npm run test:e2e:ui      # Playwright UI 모드
npm run test:e2e:headed  # 브라우저 창 보이기
npm run test:e2e:auth    # 기본 headless
```

## 2. 수동 / 임시 캡처

브라우저 스크린샷·OS 캡처 도구 결과를 `docs/screenshots/manual/<topic>/`에 직접 두는 것도 OK. session-log 스킬이 인용.

## 3. 정책

- **공개 OK**: 본 디렉터리는 git 추적. 시크릿(개인 이메일·토큰·실 사용자 데이터) **절대 X**. 더미 계정·시드 데이터만.
- **크기**: 단일 PNG 200KB 이하 권장. 큰 파일은 압축 또는 별도 영역.
- **정리**: 사용 안 하는 캡처는 주기적 정리 (분기마다).

## 4. session-log 인용 패턴

노션 페이지에 임베드 시:
```markdown
![attractions list](https://raw.githubusercontent.com/lebind12/TourDoum/develop/20-spec-tourdoum/docs/screenshots/<dir>/attractions.png)
```

GitHub raw URL이 영구 링크. 본 디렉터리가 git에 commit되어 있어야 함.

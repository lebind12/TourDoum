# scripts — 개발 흐름 헬퍼

## 평소 개발 시작

터미널 3개 권장.

```bash
# 터미널 0 — 인프라 (한 번만)
./scripts/dev-up.sh

# 터미널 1 — 백엔드 (hot reload)
# 프로젝트 루트에 .java-version=21이 박제돼 있어 jenv가 자동으로 JDK 21로 전환한다.
# (jenv 미사용자: export JAVA_HOME=$(/usr/libexec/java_home -v 21) 한 번 실행)
cd backend && ./mvnw spring-boot:run

# 터미널 2 — 프론트엔드 (HMR)
cd frontend && npm run dev
```

브라우저:

- FE: http://localhost:5173
- BE health: http://localhost:8080/api/health
- BE actuator: http://localhost:8080/actuator/health

## 끝낼 때

```bash
# 컨테이너만 멈춤 (DB 데이터 유지)
./scripts/dev-down.sh

# 전부 청소 (DB까지 날리고 처음부터 — 주의)
./scripts/dev-down.sh --wipe
```

---

## 핫리로드가 어떻게 동작하나

### Backend — Spring Boot DevTools

`spring-boot-devtools` 의존성(scope=runtime, optional=true)이 들어가면 앱이 `target/classes`를 감시한다. 클래스파일이 바뀌면 **앱 컨텍스트만** 빠르게 재시작 (전체 JVM 재시작 X).

세 가지 트리거 방식:

1. **IntelliJ 권장** — `Build → Build project` 자동 실행 ON. 저장하면 IntelliJ가 즉시 컴파일 → devtools가 감지하고 재시작.
   - `Settings → Build, Execution, Deployment → Compiler → Build project automatically` ✓
   - `Settings → Advanced Settings → Allow auto-make to start...` ✓
2. **VSCode** — Java extension의 build on save 활성화.
3. **터미널만 쓰는 경우** — 별도 창에서 변경 시 `./mvnw compile` 한 번 실행. devtools가 감지.

> 정적 리소스(`src/main/resources/templates`, `static`)는 재시작 없이 즉시 반영된다.

### Frontend — Vite HMR

`npm run dev`이면 Vite 개발 서버가 파일 변경을 감지해 **모듈 단위로** 핫 교체. 페이지 새로고침 없이 컴포넌트만 갈아 끼워진다. 별도 설정 불필요.

## 자주 만나는 문제

| 증상 | 원인/해결 |
|---|---|
| BE: `Connection refused` to mysql | `dev-up.sh` 안 돌렸거나 healthy 전. `docker compose ps`로 확인. |
| BE: redis 포트 충돌 | 본 프로젝트는 호스트 6380. `.env`/`application.yml`에서 `REDIS_PORT=6380` 확인. |
| FE: API 호출 실패 | `frontend/.env`의 `VITE_API_BASE_URL=http://localhost:8080` 확인. CORS 문제면 BE에 CORS 설정 추가 필요(추후 ADR). |
| DevTools가 재시작 안 함 | `target/classes`를 다시 만들지 않은 것. IDE auto-make 켜졌는지, 또는 `./mvnw compile`을 안 돌렸는지. |
| `release version 21 not supported` | 셸의 java가 17 등이라 그렇다. 본 프로젝트는 jenv `.java-version=21`을 박제했으나 jenv 미사용자는 수동으로 `JAVA_HOME=$(/usr/libexec/java_home -v 21)` 지정 필요. 또는 `brew install jenv` 후 `eval "$(jenv init -)"` 셸 rc에 추가. |
| `Schema-validation: missing table [members]` | 학습 단계에선 `application.yml`의 `ddl-auto=update`로 단일화돼 있어 자동 생성된다. 그래도 발생하면 `clean compile` 후 재실행. ADR-0004(Flyway 도입) 시점에 `validate`로 전환 예정. |
| `Unable to connect to Redis ... localhost/127.0.0.1:6379` | docker-compose가 redis를 호스트 6380으로 매핑한다(다른 redis와 충돌 회피). 앱은 `application.yml`의 default가 6380이라 별도 설정 불필요. 그래도 6379로 시도하면 환경변수 `REDIS_PORT` 또는 `SPRING_DATA_REDIS_PORT`를 의도치 않게 export한 상태. `unset REDIS_PORT` 후 재실행. |
| 포트 8080 사용 중 | 다른 프로세스가 점유. `lsof -i :8080`로 확인 후 종료. |

## 포트 매핑 정리

| 포트 | 용도 | 비고 |
|---|---|---|
| 8080 | BE Spring Boot | `application.yml` `server.port` |
| 5173 | FE 개발 서버 (`npm run dev`) | Vite 기본 |
| 5174 | FE Playwright e2e 전용 | `playwright.config.ts`에서 자동 부팅, 5173과 격리 |
| 3306 | MySQL | docker-compose |
| 6380 | Redis | docker-compose (호스트만 6380, 컨테이너 내부 6379) |
| 8081 | Jenkins | docker-compose, 운영 학습용 |
| 4173 | (예약) `vite preview` | 프로덕션 빌드 미리보기. 현재 미사용 |

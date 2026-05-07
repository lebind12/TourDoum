# ADR-0008: Swagger / OpenAPI 3.0 도입 (springdoc-openapi 2.6.0)

- **날짜**: 2026-05-07
- **상태**: Accepted
- **결정자**: Backend Implementer (be/feat-swagger)

---

## 맥락

FE-BE 간 API 계약이 코드와 문서 사이에 분산되어 있어 수동 동기화가 필요하다.
Postman collection을 수작업으로 유지관리하는 비용을 줄이고, 개발 중 엔드포인트를 즉시 테스트할 수 있는 UI가 필요하다.

---

## 결정

**springdoc-openapi-starter-webmvc-ui 2.6.0** 도입으로 Spring Boot 3.3.x와 통합된 OpenAPI 3.0 문서를 자동 생성한다.

### 선택 이유

| 항목 | springdoc 2.x | springfox 3.x |
|---|---|---|
| Spring Boot 3.x 지원 | ✅ 공식 | ❌ 미지원 (3.x 미호환) |
| Jakarta EE 9+ | ✅ | ❌ |
| OpenAPI 3.0 | ✅ | 부분 |
| 마지막 릴리즈 | 활발 | 2022년 이후 중단 |

springfox는 Spring Boot 3.x (Jakarta EE 9)와 호환되지 않으므로 springdoc을 채택한다.

---

## 세부 구현

### 1. 의존성

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

Spring Boot BOM 미관리이므로 버전을 명시한다. 업그레이드 시 `mvnw versions:display-dependency-updates` 로 확인.

### 2. 공개 경로 (SecurityConfig)

```
/swagger-ui/**
/swagger-ui.html
/v3/api-docs/**
/v3/api-docs
```

이 경로들을 `permitAll()` 로 등록해 인증 없이 접근 가능하게 한다.

> **운영 주의**: prod에서는 `springdoc.swagger-ui.enabled=false`로 비활성화해야 한다.

### 3. 세션 쿠키 SecurityScheme

```java
@SecurityScheme(
    name = "SESSION",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = "SESSION")
```

Spring Session이 발급하는 `SESSION` 쿠키를 OpenAPI SecurityScheme으로 정의한다.
Swagger UI에서 `/api/auth/login` 성공 후 브라우저에 쿠키가 저장되며,
이후 보호 엔드포인트 테스트 시 자동 포함된다.

### 4. 컨트롤러 어노테이션 규칙

| 어노테이션 | 위치 | 내용 |
|---|---|---|
| `@Tag(name, description)` | 클래스 | 컨트롤러 그룹명 |
| `@Operation(summary, description)` | 메서드 | 엔드포인트 요약·설명 |
| `@ApiResponse(responseCode, description)` | 메서드 | 비표준 응답 코드 문서화 |
| `@Parameter(description, required)` | 파라미터 | 파라미터 설명 |
| `@SecurityRequirement(name = "SESSION")` | 메서드 | 인증 필요 엔드포인트 표시 |

**1차 적용 대상**: Auth, Member, Attraction, Health 컨트롤러
**후속 적용**: Favorite, Accommodation, Chat 컨트롤러 (도메인 구현 시 동시 추가)

### 5. application.yml 설정

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: alpha   # 알파벳 순
    tags-sorter: alpha
    display-request-duration: true
    csrf:
      enabled: false           # CSRF 비활성 환경과 정합
  api-docs:
    path: /v3/api-docs
```

---

## 결과

- `GET /swagger-ui.html` → 브라우저 인터랙티브 API 문서
- `GET /v3/api-docs` → OpenAPI 3.0 JSON (Postman/Insomnia import 가능)
- Auth/Member/Attraction/Health 컨트롤러 1차 문서화 완료
- 후속 도메인 컨트롤러는 동일 패턴 적용 (`backend/README.md` 참고)

---

## 주의 사항

1. **prod 비활성화**: `springdoc.swagger-ui.enabled=false` 를 prod 프로파일에 추가할 것.
   현재 dev 단계이므로 활성 상태로 유지.

2. **API 응답 예시에 시크릿 금지**: `@ExampleObject` 등에 실 DB 데이터·키·비밀번호를 삽입하지 않는다.

3. **쿠키 크로스오리진**: Swagger UI가 다른 포트에서 접근 시 쿠키 자동 전송이 안 될 수 있다.
   로컬 개발 환경에서는 `http://localhost:8080/swagger-ui.html`로 직접 접근한다.

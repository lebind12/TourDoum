package com.ssafy.tourdoum.global;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 메타데이터 및 보안 스키마 설정 (ADR-0008).
 *
 * <p>세션 쿠키 인증 방식: SpringSecurity + Spring Session이 발급하는 {@code SESSION} 쿠키를 SecurityScheme으로 정의한다.
 * Swagger UI에서 로그인 후 브라우저가 SESSION 쿠키를 자동 포함(same-origin)하므로 별도 Authorize 입력 없이 보호 엔드포인트를 테스트할 수
 * 있다.
 *
 * <p>운영 주의: prod 전환 시 {@code springdoc.swagger-ui.enabled=false}로 노출을 막는다.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "TourDoum API",
            version = "0.1.0",
            description = "전국 여행지 + 주변 숙박 추천 API",
            contact = @Contact(name = "lebind12", url = "https://github.com/lebind12/TourDoum")),
    servers = {@Server(url = "http://localhost:8080", description = "로컬 개발 서버")})
@SecurityScheme(
    name = "SESSION",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = "SESSION",
    description =
        "Spring Session 쿠키. POST /api/auth/login 성공 시 브라우저에 자동 저장됨. "
            + "Swagger UI에서는 same-origin 요청이므로 별도 입력 없이 자동 전송.")
public class OpenApiConfig {}

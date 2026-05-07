package com.ssafy.tourdoum.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.tourdoum.member.Member;
import com.ssafy.tourdoum.member.MemberRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 6.x 설정. ADR-0003: 폼 로그인(JSON body) + Redis 세션 + BCrypt 비번 해시. CSRF: dev 비활성
 * (TODO: prod 활성화 - handoff.md 참고).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final MemberDetailsService memberDetailsService;
  private final ObjectMapper objectMapper;
  private final MemberRepository memberRepository;

  /**
   * 허용 origin 목록. 쉼표 구분. default: 사용자 로컬 dev(5173/5174) + agent worktree(30173/30174). 운영 환경에선 배포
   * URL을 명시 주입.
   */
  @Value(
      "${tourdoum.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:30173,http://localhost:30174}")
  private List<String> allowedOrigins;

  public SecurityConfig(
      MemberDetailsService memberDetailsService,
      ObjectMapper objectMapper,
      MemberRepository memberRepository) {
    this.memberDetailsService = memberDetailsService;
    this.objectMapper = objectMapper;
    this.memberRepository = memberRepository;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(allowedOrigins);
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setExposedHeaders(List.of("Set-Cookie"));
    cfg.setAllowCredentials(true); // 세션 쿠키 송수신 허용
    cfg.setMaxAge(3600L); // preflight 캐시 1시간

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager() {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(memberDetailsService);
    provider.setPasswordEncoder(passwordEncoder());
    return new ProviderManager(provider);
  }

  @Bean
  public JsonAuthenticationFilter jsonAuthenticationFilter() throws Exception {
    JsonAuthenticationFilter filter = new JsonAuthenticationFilter(objectMapper);
    filter.setFilterProcessesUrl("/api/auth/login");
    filter.setAuthenticationManager(authenticationManager());
    filter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());

    // 로그인 성공: 200 OK + SESSION 쿠키 자동 발급 + MeResponse({id,email,nickname,role}) 반환
    filter.setAuthenticationSuccessHandler(
        (request, response, authentication) -> {
          String email = authentication.getName();
          Member member =
              memberRepository
                  .findByEmail(email)
                  .orElseThrow(() -> new IllegalStateException("인증된 회원을 DB에서 찾을 수 없습니다: " + email));
          response.setStatus(HttpServletResponse.SC_OK);
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          response.setCharacterEncoding("UTF-8");
          objectMapper.writeValue(response.getWriter(), MeResponse.from(member));
        });

    // 로그인 실패: 401 Unauthorized
    filter.setAuthenticationFailureHandler(
        (request, response, exception) -> {
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          response.setCharacterEncoding("UTF-8");
          response.getWriter().write("{\"message\":\"이메일 또는 비밀번호가 올바르지 않습니다.\"}");
        });

    return filter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // CORS: 별도 빈으로 설정 주입 (Spring Security가 우선 처리)
        .cors(Customizer.withDefaults())

        // CSRF: dev 비활성 (TODO: prod에서 SameSite=Lax로 완화 후 활성화 검토)
        .csrf(AbstractHttpConfigurer::disable)

        // 세션 관리: 필요 시 세션 생성 (기본값이나 명시 필요 — Spring Security 6 호환)
        // IF_REQUIRED: 인증 후 자동 세션 생성. STATELESS는 SESSION 쿠키 발급 불가이므로 사용 금지.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

        // SecurityContext 저장소: requireExplicitSave(false) → SecurityContextPersistenceFilter
        // 모드로 전환.
        // Spring Security 6 기본(requireExplicitSave=true)은 SecurityContextHolderFilter를 사용하며
        // 커스텀 JsonAuthenticationFilter와 결합 시 SESSION 쿠키가 응답에 실리지 않는 버그가 발생.
        // HttpSessionSecurityContextRepository를 공유 사용하여 로그인/후속 요청 모두 세션 참조 일관성 보장.
        .securityContext(
            ctx ->
                ctx.securityContextRepository(new HttpSessionSecurityContextRepository())
                    .requireExplicitSave(false))

        // 인가 규칙
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/**",
                        "/api/health",
                        "/api/members/signup",
                        "/api/attractions/**",
                        "/api/accommodations/**",
                        "/actuator/**",
                        "/actuator/health",
                        // Swagger UI + OpenAPI spec (ADR-0008) — dev 전용 공개
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs")
                    .permitAll()
                    // 후기 목록/집계는 공개; 작성·삭제는 인증 필수 (POST/DELETE는 anyRequest().authenticated()로 처리)
                    .requestMatchers(HttpMethod.GET, "/api/reviews", "/api/reviews/summary")
                    .permitAll()
                    .anyRequest()
                    .authenticated())

        // JSON 로그인 필터: UsernamePasswordAuthenticationFilter 교체
        .addFilterAt(jsonAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)

        // 로그아웃
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .deleteCookies("SESSION")
                    .invalidateHttpSession(true)
                    .logoutSuccessHandler(
                        (request, response, authentication) ->
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT)))

        // 401/403 JSON 응답
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authException) -> {
                          response.setStatus(HttpStatus.UNAUTHORIZED.value());
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response.setCharacterEncoding("UTF-8");
                          String body =
                              objectMapper.writeValueAsString(Map.of("message", "인증이 필요합니다."));
                          response.getWriter().write(body);
                        })
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          response.setStatus(HttpStatus.FORBIDDEN.value());
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response.setCharacterEncoding("UTF-8");
                          String body =
                              objectMapper.writeValueAsString(Map.of("message", "접근 권한이 없습니다."));
                          response.getWriter().write(body);
                        }));

    return http.build();
  }
}

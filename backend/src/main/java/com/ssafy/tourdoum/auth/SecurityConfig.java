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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
   * 허용 origin 목록. 쉼표 구분. dev 기본값은 Vite dev 서버(5173) + Playwright 전용(5174). 운영 환경에선 배포 URL을 명시 주입.
   */
  @Value("${tourdoum.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
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

        // 인가 규칙
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/**",
                        "/api/health",
                        "/api/members/signup",
                        "/api/attractions/**",
                        "/actuator/**",
                        "/actuator/health")
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

package com.ssafy.tourdoum.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 설정 — JWT (ADR-0011 BE-1, #61).
 *
 * <p>이전 ADR-0003 (form login + Spring Session) 폐기. STATELESS, {@link JwtAuthenticationFilter}로
 * Authorization: Bearer header를 SecurityContext에 매핑.
 *
 * <p>cookie 기반(httpOnly + Secure + SameSite=Strict) + CSRF는 BE-3 (ADR-0011) 범위.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

  private final MemberDetailsService memberDetailsService;
  private final ObjectMapper objectMapper;
  private final JwtTokenProvider tokenProvider;

  /** 허용 origin 목록. dev 기본값: Vite(5173) + Playwright(5174). */
  @Value("${tourdoum.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
  private List<String> allowedOrigins;

  public SecurityConfig(
      MemberDetailsService memberDetailsService,
      ObjectMapper objectMapper,
      JwtTokenProvider tokenProvider) {
    this.memberDetailsService = memberDetailsService;
    this.objectMapper = objectMapper;
    this.tokenProvider = tokenProvider;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(allowedOrigins);
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    // BE-3에서 cookie 흐름 추가 시 Set-Cookie 노출 필요. 현 BE-1은 Bearer header 응답만.
    cfg.setExposedHeaders(List.of("Authorization"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);

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
  public JwtAuthenticationFilter jwtAuthenticationFilter() {
    return new JwtAuthenticationFilter(tokenProvider);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // CORS
        .cors(Customizer.withDefaults())

        // CSRF: BE-1은 헤더 기반(Bearer), CSRF 무관. cookie 흐름이 추가되는 BE-3에서 정식 박제.
        .csrf(AbstractHttpConfigurer::disable)

        // STATELESS — ADR-0011 핵심. Spring Security가 HttpSession을 만들거나 사용하지 않음.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 폼 로그인 / HTTP basic / logout 핸들러 명시 비활성 (이전 form 로그인 흔적 제거).
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)

        // 인가 규칙 (변경 없음 — 기존과 동일)
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
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/reviews", "/api/reviews/summary")
                    .permitAll()
                    .anyRequest()
                    .authenticated())

        // JWT 필터: UsernamePasswordAuthenticationFilter 위치 앞에 등록.
        .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)

        // 401/403 JSON 응답
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authException) -> {
                          response.setStatus(HttpStatus.UNAUTHORIZED.value());
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response.setCharacterEncoding("UTF-8");
                          response
                              .getWriter()
                              .write(
                                  objectMapper.writeValueAsString(Map.of("message", "인증이 필요합니다.")));
                        })
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response.setCharacterEncoding("UTF-8");
                          response
                              .getWriter()
                              .write(
                                  objectMapper.writeValueAsString(
                                      Map.of("message", "접근 권한이 없습니다.")));
                        }));

    return http.build();
  }
}

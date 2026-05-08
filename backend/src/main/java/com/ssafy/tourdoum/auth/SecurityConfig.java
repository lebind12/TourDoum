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
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 설정 — JWT (ADR-0011).
 *
 * <ul>
 *   <li>BE-1 (#61): Bearer header 인증 + STATELESS.
 *   <li>BE-2 (#63): logout 인증 게이트.
 *   <li>BE-3 (본 변경): refresh transport를 httpOnly cookie로 박고, double-submit CSRF token 강제.
 *       login/signup은 사전인증 단계라 CSRF 면제. {@link CsrfCookieFilter}로 매 응답 XSRF-TOKEN cookie
 *       materialize.
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, AuthCookieProperties.class})
public class SecurityConfig {

  private final MemberDetailsService memberDetailsService;
  private final ObjectMapper objectMapper;
  private final JwtTokenProvider tokenProvider;
  private final AccessTokenDenylist denylist;
  private final UserRevocationStore revocationStore;

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
      JwtTokenProvider tokenProvider,
      AccessTokenDenylist denylist,
      UserRevocationStore revocationStore) {
    this.memberDetailsService = memberDetailsService;
    this.objectMapper = objectMapper;
    this.tokenProvider = tokenProvider;
    this.denylist = denylist;
    this.revocationStore = revocationStore;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(allowedOrigins);
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    // BE-3: refresh cookie + XSRF-TOKEN cookie는 브라우저 기본 처리. JS가 읽을 헤더만 expose.
    cfg.setExposedHeaders(List.of("Authorization"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }

  /**
   * Password encoder — ADR-0011 BE-4.1.
   *
   * <p>Default: Argon2id (m=64MiB, t=3, p=1). Bcrypt(cost=12) fallback for legacy 박제.
   * DelegatingPasswordEncoder가 prefix로 알고리즘 분기 → 기존 {bcrypt} 해시는 그대로 검증되며
   * {@code upgradeEncoding}이 true이므로 로그인 성공 시 호출자가 rehash 트리거. 학습 단계 IT는 비용을 줄이기 위해
   * {@code tourdoum.auth.argon2.memory-kib}로 m을 축소할 수 있다(기본 65536).
   */
  @Bean
  public PasswordEncoder passwordEncoder(
      @Value("${tourdoum.auth.argon2.memory-kib:65536}") int memoryKib,
      @Value("${tourdoum.auth.argon2.iterations:3}") int iterations,
      @Value("${tourdoum.auth.argon2.parallelism:1}") int parallelism,
      @Value("${tourdoum.auth.bcrypt.strength:12}") int bcryptStrength) {
    String idForEncode = "argon2id";
    java.util.Map<String, PasswordEncoder> encoders = new java.util.HashMap<>();
    // Argon2id — 16B salt, 32B hash, m=memoryKib KiB, t=iterations, p=parallelism.
    encoders.put(idForEncode, new Argon2PasswordEncoder(16, 32, parallelism, memoryKib, iterations));
    encoders.put("bcrypt", new BCryptPasswordEncoder(bcryptStrength));
    DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder(idForEncode, encoders);
    // 기본 fallback — 옛 시드 데이터에 prefix 없는 raw bcrypt가 있을 가능성 방어. matches 단계에서만 사용.
    delegating.setDefaultPasswordEncoderForMatches(
        PasswordEncoderFactories.createDelegatingPasswordEncoder());
    return delegating;
  }

  @Bean
  public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(memberDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter() {
    return new JwtAuthenticationFilter(tokenProvider, denylist, revocationStore);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    // CSRF — double-submit cookie pattern. JS가 XSRF-TOKEN cookie를 읽고 X-XSRF-TOKEN 헤더로 보낸다.
    // XOR 마스킹은 끄고(plain handler) 단순 동등 비교 — SPA double-submit 호환.
    CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepo.setCookiePath("/"); // 모든 경로에서 전송 (refresh/logout 외 mutation도 동일).
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    csrfHandler.setCsrfRequestAttributeName(null); // deferred 비활성: 매 요청에 token 즉시 resolve.

    http
        // CORS
        .cors(Customizer.withDefaults())

        // CSRF: cookie 기반 double-submit. 사전인증 단계(login/signup)는 면제.
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfRepo)
                    .csrfTokenRequestHandler(csrfHandler)
                    .ignoringRequestMatchers(
                        "/api/auth/login",
                        "/api/members/signup",
                        "/api/auth/password-reset/initiate",
                        "/api/auth/password-reset/complete"))

        // STATELESS — ADR-0011 핵심. Spring Security가 HttpSession을 만들거나 사용하지 않음.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 폼 로그인 / HTTP basic / logout 핸들러 명시 비활성.
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)

        // 인가 규칙 — BE-2: logout은 인증 필요. BE-3: CSRF는 csrf() 단계에서 별도 게이트.
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/auth/logout")
                    .authenticated()
                    // BE-4.3: 비밀번호 변경은 인증 필요 + CSRF 적용(default).
                    .requestMatchers(HttpMethod.POST, "/api/auth/password")
                    .authenticated()
                    .requestMatchers(
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

        // CSRF cookie materialize: CsrfFilter 뒤에 박혀 매 응답에서 XSRF-TOKEN을 채워 보낸다.
        .addFilterAfter(
            new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)

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

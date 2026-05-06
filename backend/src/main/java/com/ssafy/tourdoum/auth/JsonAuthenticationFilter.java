package com.ssafy.tourdoum.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JSON 바디로 로그인 요청을 받는 커스텀 필터. 기본 폼 로그인 필터는 form-urlencoded 파라미터를 사용하므로, JSON body({email,
 * password})를 처리하도록 오버라이드.
 */
public class JsonAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

  private final ObjectMapper objectMapper;

  public JsonAuthenticationFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Authentication attemptAuthentication(
      HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
    if (!request.getMethod().equals("POST")) {
      throw new AuthenticationServiceException(
          "Authentication method not supported: " + request.getMethod());
    }

    LoginRequest loginRequest;
    try {
      loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
    } catch (IOException e) {
      throw new AuthenticationServiceException("로그인 요청 파싱 실패", e);
    }

    String email = loginRequest.email() != null ? loginRequest.email().trim() : "";
    String password = loginRequest.password() != null ? loginRequest.password() : "";

    UsernamePasswordAuthenticationToken authRequest =
        UsernamePasswordAuthenticationToken.unauthenticated(email, password);
    setDetails(request, authRequest);

    return this.getAuthenticationManager().authenticate(authRequest);
  }
}

package com.ssafy.tourdoum.global;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

/**
 * Redis 세션 설정. ADR-0003: GenericJackson2JsonRedisSerializer 사용 — 클래스 정보(@class) 보존, JDK 직렬화 대비 클래스
 * 시그니처 변경에 안전.
 *
 * <p>Spring Security 객체(Authentication, DefaultSavedRequest 등)는 default constructor가 없어 Jackson이
 * 역직렬화하지 못한다. {@link SecurityJackson2Modules}가 제공하는 mixin들을 ObjectMapper에 등록해 해결.
 *
 * <p>ADR-0005: {@code spring.session.store-type=redis} 일 때만 이 설정을 로드한다 (matchIfMissing=true). 테스트
 * 컨텍스트에서 store-type=none 설정 시 @EnableRedisIndexedHttpSession 이 Redis 연결을 시도하지 않도록 방어한다. store-type
 * 프로퍼티가 없으면 기존 동작(Redis 세션 활성화)을 유지한다.
 */
@Configuration
@ConditionalOnProperty(
    name = "spring.session.store-type",
    havingValue = "redis",
    matchIfMissing = true)
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 1800) // 30분 idle
public class RedisSessionConfig {

  /**
   * Spring Session이 사용하는 기본 Redis 직렬화기 빈. 이름 'springSessionDefaultRedisSerializer'를 사용하면 Spring
   * Session이 자동으로 이 빈을 채택.
   */
  @Bean("springSessionDefaultRedisSerializer")
  public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
    ObjectMapper mapper = new ObjectMapper();

    // Spring Security가 세션에 넣는 객체들(SecurityContext / Authentication / DefaultSavedRequest 등)을
    // 안전하게 직렬화/역직렬화하기 위한 mixin 모듈 등록. 이게 빠지면 보호 라우트 첫 접근 시
    // RequestCache가 저장한 DefaultSavedRequest를 다시 읽을 때 InvalidDefinitionException이 발생한다.
    mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));

    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // 클래스 타입 정보 포함 (역직렬화 시 필요). SecurityJackson2Modules가 자체 typing을 활성화하지만
    // 도메인 객체에도 class info를 박기 위해 추가 typing 유지.
    mapper.activateDefaultTyping(
        mapper.getPolymorphicTypeValidator(),
        ObjectMapper.DefaultTyping.NON_FINAL,
        com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);

    return new GenericJackson2JsonRedisSerializer(mapper);
  }
}

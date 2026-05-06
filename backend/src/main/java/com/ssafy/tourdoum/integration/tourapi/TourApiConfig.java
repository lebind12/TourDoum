package com.ssafy.tourdoum.integration.tourapi;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * TourAPI 통합 설정 클래스.
 *
 * <p>ADR-0005: TourApiProperties 를 @ConfigurationProperties 로 활성화하고 RestClient.Builder 빈을 통해
 * TourApiClient에 주입.
 */
@Configuration
@EnableConfigurationProperties(TourApiProperties.class)
public class TourApiConfig {

  @Bean
  public RestClient.Builder tourApiRestClientBuilder() {
    return RestClient.builder();
  }
}

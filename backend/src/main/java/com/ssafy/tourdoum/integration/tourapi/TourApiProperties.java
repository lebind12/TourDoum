package com.ssafy.tourdoum.integration.tourapi;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TourAPI 4.0 (KorService2) 설정.
 *
 * <p>ADR-0005: serviceKey는 환경변수 TOUR_API_KEY로 주입. 코드·로그에 노출 금지.
 */
@ConfigurationProperties(prefix = "tourapi")
public record TourApiProperties(
    String baseUrl,
    String serviceKey,
    String mobileApp,
    int pageSize,
    List<Integer> areaCodes,
    List<Integer> contentTypeIds) {}

package com.ssafy.tourdoum.integration.tourapi;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 한국관광공사 TourAPI 4.0 (KorService2) HTTP 클라이언트.
 *
 * <p>ADR-0005: Spring RestClient 사용 (spring-boot-starter-web 내장, 추가 의존성 불필요). serviceKey는 URL 파라미터로
 * 전달하되 로그에 절대 출력하지 않는다.
 */
@Component
public class TourApiClient {

  private static final Logger log = LoggerFactory.getLogger(TourApiClient.class);

  private static final String AREA_BASED_LIST_PATH = "/areaBasedList2";
  private static final String SUCCESS_CODE = "0000";

  private final RestClient restClient;
  private final TourApiProperties props;

  public TourApiClient(RestClient.Builder restClientBuilder, TourApiProperties props) {
    this.restClient = restClientBuilder.baseUrl(props.baseUrl()).build();
    this.props = props;
  }

  /**
   * areaBasedList2 단일 페이지 조회.
   *
   * @param areaCode 지역코드 (1=서울, …, 39=제주)
   * @param contentTypeId 콘텐츠 유형 (12=관광지, 14=문화시설, 28=레포츠)
   * @param pageNo 페이지 번호 (1부터)
   * @return 응답 아이템 목록. API 오류 시 빈 리스트.
   */
  public List<TourApiAreaItem> fetchAreaBasedList(int areaCode, int contentTypeId, int pageNo) {
    String uri =
        UriComponentsBuilder.fromPath(AREA_BASED_LIST_PATH)
            .queryParam("serviceKey", props.serviceKey())
            .queryParam("MobileOS", "ETC")
            .queryParam("MobileApp", props.mobileApp())
            .queryParam("_type", "json")
            .queryParam("numOfRows", props.pageSize())
            .queryParam("pageNo", pageNo)
            .queryParam("areaCode", areaCode)
            .queryParam("contentTypeId", contentTypeId)
            .build()
            .toUriString();

    try {
      TourApiResponse response = restClient.get().uri(uri).retrieve().body(TourApiResponse.class);

      if (response == null || response.response() == null) {
        log.warn(
            "TourAPI: null 응답 areaCode={} contentTypeId={} page={}",
            areaCode,
            contentTypeId,
            pageNo);
        return List.of();
      }

      String resultCode = response.response().header().resultCode();
      if (!SUCCESS_CODE.equals(resultCode)) {
        log.warn(
            "TourAPI: resultCode={} msg={} areaCode={} contentTypeId={} page={}",
            resultCode,
            response.response().header().resultMsg(),
            areaCode,
            contentTypeId,
            pageNo);
        return List.of();
      }

      TourApiResponse.Body body = response.response().body();
      if (body == null || body.items() == null) {
        return List.of();
      }

      return body.items().itemSafe();

    } catch (Exception e) {
      log.warn(
          "TourAPI: 호출 실패 areaCode={} contentTypeId={} page={} — {}",
          areaCode,
          contentTypeId,
          pageNo,
          e.getMessage());
      return List.of();
    }
  }

  /**
   * 지역 + contentTypeId의 전체 페이지를 수집.
   *
   * <p>첫 페이지 응답의 totalCount로 총 페이지 수 계산. 페이지 실패 시 해당 페이지 스킵.
   */
  public List<TourApiAreaItem> fetchAll(int areaCode, int contentTypeId) {
    // 첫 페이지 조회로 totalCount 파악
    String firstUri =
        UriComponentsBuilder.fromPath(AREA_BASED_LIST_PATH)
            .queryParam("serviceKey", props.serviceKey())
            .queryParam("MobileOS", "ETC")
            .queryParam("MobileApp", props.mobileApp())
            .queryParam("_type", "json")
            .queryParam("numOfRows", props.pageSize())
            .queryParam("pageNo", 1)
            .queryParam("areaCode", areaCode)
            .queryParam("contentTypeId", contentTypeId)
            .build()
            .toUriString();

    TourApiResponse firstResponse;
    try {
      firstResponse = restClient.get().uri(firstUri).retrieve().body(TourApiResponse.class);
    } catch (Exception e) {
      log.warn(
          "TourAPI: 첫 페이지 조회 실패 areaCode={} contentTypeId={} — {}",
          areaCode,
          contentTypeId,
          e.getMessage());
      return List.of();
    }

    if (firstResponse == null
        || firstResponse.response() == null
        || !SUCCESS_CODE.equals(firstResponse.response().header().resultCode())) {
      String code =
          firstResponse != null && firstResponse.response() != null
              ? firstResponse.response().header().resultCode()
              : "null";
      log.warn(
          "TourAPI: resultCode={} areaCode={} contentTypeId={}", code, areaCode, contentTypeId);
      return List.of();
    }

    TourApiResponse.Body firstBody = firstResponse.response().body();
    if (firstBody == null) {
      return List.of();
    }

    java.util.List<TourApiAreaItem> result = new java.util.ArrayList<>();
    if (firstBody.items() != null) {
      result.addAll(firstBody.items().itemSafe());
    }

    int totalCount = firstBody.totalCount();
    int numOfRows = props.pageSize();
    int totalPages = (totalCount + numOfRows - 1) / numOfRows;

    for (int page = 2; page <= totalPages; page++) {
      List<TourApiAreaItem> items = fetchAreaBasedList(areaCode, contentTypeId, page);
      result.addAll(items);
    }

    return result;
  }
}

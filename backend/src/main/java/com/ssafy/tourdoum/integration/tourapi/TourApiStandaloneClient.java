package com.ssafy.tourdoum.integration.tourapi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TourAPI 4.0 독립 HTTP 클라이언트 (java.net.http.HttpClient 기반).
 *
 * <p>Spring 컨텍스트 없이 {@link TourApiSqlGeneratorMain}에서 직접 사용. serviceKey는 URL 파라미터로만 전달하며, 로그·출력에 노출
 * 금지.
 */
public class TourApiStandaloneClient {

  private static final Logger log = LoggerFactory.getLogger(TourApiStandaloneClient.class);
  private static final String SUCCESS_CODE = "0000";

  private final TourApiProperties props;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public TourApiStandaloneClient(TourApiProperties props, ObjectMapper objectMapper) {
    this.props = props;
    this.objectMapper =
        objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /** 지역 + contentTypeId 전체 페이지 수집. */
  public List<TourApiAreaItem> fetchAll(int areaCode, int contentTypeId) throws Exception {
    List<TourApiAreaItem> result = new ArrayList<>();

    TourApiResponse firstResp = fetchPage(areaCode, contentTypeId, 1);
    if (firstResp == null
        || firstResp.response() == null
        || !SUCCESS_CODE.equals(firstResp.response().header().resultCode())) {
      String code =
          (firstResp != null && firstResp.response() != null)
              ? firstResp.response().header().resultCode()
              : "null";
      String msg =
          (firstResp != null && firstResp.response() != null)
              ? firstResp.response().header().resultMsg()
              : "응답없음";
      log.warn(
          "TourAPI 오류: resultCode={} msg={} areaCode={} contentTypeId={}",
          code,
          msg,
          areaCode,
          contentTypeId);
      return List.of();
    }

    TourApiResponse.Body body = firstResp.response().body();
    if (body != null && body.items() != null) {
      result.addAll(body.items().itemSafe());
    }

    if (body == null) return result;

    int totalCount = body.totalCount();
    int numOfRows = props.pageSize();
    int totalPages = (totalCount + numOfRows - 1) / numOfRows;

    for (int page = 2; page <= totalPages; page++) {
      try {
        TourApiResponse resp = fetchPage(areaCode, contentTypeId, page);
        if (resp != null
            && resp.response() != null
            && SUCCESS_CODE.equals(resp.response().header().resultCode())
            && resp.response().body() != null
            && resp.response().body().items() != null) {
          result.addAll(resp.response().body().items().itemSafe());
        }
      } catch (Exception e) {
        log.warn(
            "페이지 {} 스킵 areaCode={} contentTypeId={}: {}",
            page,
            areaCode,
            contentTypeId,
            e.getMessage());
      }
    }

    return result;
  }

  private TourApiResponse fetchPage(int areaCode, int contentTypeId, int pageNo) throws Exception {
    String url =
        props.baseUrl()
            + "/areaBasedList2"
            + "?serviceKey="
            + props.serviceKey()
            + "&MobileOS=ETC"
            + "&MobileApp="
            + props.mobileApp()
            + "&_type=json"
            + "&numOfRows="
            + props.pageSize()
            + "&pageNo="
            + pageNo
            + "&areaCode="
            + areaCode
            + "&contentTypeId="
            + contentTypeId;

    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    String body = response.body();

    return objectMapper.readValue(body, TourApiResponse.class);
  }
}

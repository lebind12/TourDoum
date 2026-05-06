package com.ssafy.tourdoum.integration.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * TourApiClient 단위 테스트.
 *
 * <p>MockRestServiceServer 로 네트워크 없이 응답을 모킹. Spring 6.1+ 에서 RestClient.Builder 와
 * MockRestServiceServer 를 함께 사용한다.
 */
class TourApiClientTest {

  private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";

  private MockRestServiceServer mockServer;
  private TourApiClient client;

  @BeforeEach
  void setUp() {
    TourApiProperties props =
        new TourApiProperties(BASE_URL, "test-key", "tourdoum", 100, List.of(1), List.of(12));

    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    mockServer = MockRestServiceServer.bindTo(builder).build();
    client = new TourApiClient(builder, props);
  }

  @Test
  @DisplayName("정상 응답 → 아이템 리스트 반환")
  void fetchAreaBasedList_success_returnsItems() {
    String mockBody =
        """
        {
          "response": {
            "header": { "resultCode": "0000", "resultMsg": "OK" },
            "body": {
              "items": {
                "item": [
                  {
                    "contentid": "126559",
                    "contenttypeid": "12",
                    "title": "경복궁",
                    "addr1": "서울특별시 종로구 사직로 161",
                    "addr2": "",
                    "mapx": "126.976898",
                    "mapy": "37.579617",
                    "firstimage": "https://example.com/img.jpg",
                    "areacode": "1"
                  }
                ]
              },
              "totalCount": 1,
              "numOfRows": 100,
              "pageNo": 1
            }
          }
        }
        """;

    mockServer
        .expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
        .andExpect(queryParam("areaCode", "1"))
        .andExpect(queryParam("contentTypeId", "12"))
        .andExpect(queryParam("pageNo", "1"))
        .andRespond(withSuccess(mockBody, MediaType.APPLICATION_JSON));

    List<TourApiAreaItem> items = client.fetchAreaBasedList(1, 12, 1);

    mockServer.verify();
    assertThat(items).hasSize(1);
    TourApiAreaItem item = items.get(0);
    assertThat(item.contentId()).isEqualTo("126559");
    assertThat(item.title()).isEqualTo("경복궁");
    assertThat(item.mapx()).isEqualTo("126.976898");
    assertThat(item.mapy()).isEqualTo("37.579617");
    assertThat(item.areaCode()).isEqualTo("1");
  }

  @Test
  @DisplayName("resultCode != 0000 → 빈 리스트 반환")
  void fetchAreaBasedList_invalidKey_returnsEmpty() {
    String mockBody =
        """
        {
          "response": {
            "header": {
              "resultCode": "99",
              "resultMsg": "SERVICE KEY IS NOT REGISTERED ERROR."
            },
            "body": { "items": {}, "totalCount": 0, "numOfRows": 100, "pageNo": 1 }
          }
        }
        """;

    mockServer
        .expect(requestTo(org.hamcrest.Matchers.containsString("/areaBasedList2")))
        .andRespond(withSuccess(mockBody, MediaType.APPLICATION_JSON));

    List<TourApiAreaItem> items = client.fetchAreaBasedList(1, 12, 1);

    mockServer.verify();
    assertThat(items).isEmpty();
  }
}

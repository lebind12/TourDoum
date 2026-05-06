package com.ssafy.tourdoum.integration.tourapi;

import com.ssafy.tourdoum.integration.tourapi.TourApiAttractionMapper.SqlInsertRow;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * TourAPI 수집 서비스.
 *
 * <p>모든 지역 + contentTypeId 조합을 순회해 SqlInsertRow 목록을 반환한다.
 * 중복 tour_api_id는 Set으로 제거.
 */
@Service
public class TourApiImportService {

  private static final Logger log = LoggerFactory.getLogger(TourApiImportService.class);

  private final TourApiClient client;
  private final TourApiAttractionMapper mapper;
  private final TourApiProperties props;

  public TourApiImportService(
      TourApiClient client, TourApiAttractionMapper mapper, TourApiProperties props) {
    this.client = client;
    this.mapper = mapper;
    this.props = props;
  }

  /**
   * 전체 지역 × contentTypeId 수집.
   *
   * @return 변환 완료된 SqlInsertRow 목록 (중복 tourApiId 제거됨)
   */
  public List<SqlInsertRow> collectAll() {
    List<SqlInsertRow> result = new ArrayList<>();
    Set<String> seenTourApiIds = new LinkedHashSet<>();

    int totalFetched = 0;
    int totalSkipped = 0;
    int totalDuplicate = 0;

    for (int contentTypeId : props.contentTypeIds()) {
      for (int areaCode : props.areaCodes()) {
        log.info("수집 중: areaCode={} contentTypeId={}", areaCode, contentTypeId);

        List<TourApiAreaItem> items = client.fetchAll(areaCode, contentTypeId);
        totalFetched += items.size();

        for (TourApiAreaItem item : items) {
          SqlInsertRow row = mapper.map(item);
          if (row == null) {
            totalSkipped++;
            continue;
          }
          if (row.tourApiId() != null && seenTourApiIds.contains(row.tourApiId())) {
            totalDuplicate++;
            continue;
          }
          if (row.tourApiId() != null) {
            seenTourApiIds.add(row.tourApiId());
          }
          result.add(row);
        }
      }
    }

    log.info(
        "수집 완료 — 전체: {} 건 / 변환 성공: {} 건 / 스킵(좌표·타이틀 누락): {} 건 / 중복: {} 건",
        totalFetched,
        result.size(),
        totalSkipped,
        totalDuplicate);

    return result;
  }
}

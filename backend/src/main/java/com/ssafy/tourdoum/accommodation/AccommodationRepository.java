package com.ssafy.tourdoum.accommodation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 숙박 레포지토리. */
public interface AccommodationRepository extends JpaRepository<Accommodation, Long> {

  /**
   * 이름/주소 LIKE 후보 조회 (KmpMatcher 재검증 전 단계).
   *
   * <p>AttractionRepository.findKeywordCandidates 동일 패턴.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT * FROM accommodations
          WHERE name    LIKE CONCAT('%', :keyword, '%')
             OR address LIKE CONCAT('%', :keyword, '%')
          ORDER BY id
          LIMIT :limit
          """)
  List<Accommodation> findKeywordCandidates(
      @Param("keyword") String keyword, @Param("limit") int limit);

  /**
   * 주어진 좌표로부터 반경 내 숙박을 거리 순으로 반환.
   *
   * <p>ADR-0002: location 컬럼은 DB GENERATED STORED POINT(lng, lat) SRID 4326. ST_Distance_Sphere는 미터
   * 단위 구면 거리를 계산한다. H2는 이 함수를 지원하지 않으므로 공간 검색은 MySQL Testcontainers 통합 테스트에서 검증한다.
   *
   * <p>ADR-0007: {@code thumbnail_url AS thumbnailUrl} 별칭은 projection getter {@code
   * getThumbnailUrl()}과 대응한다.
   *
   * @param lat 위도 (WGS84)
   * @param lng 경도 (WGS84)
   * @param radiusMeters 반경 (미터)
   * @param limit 최대 반환 건수
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT id, name, type, address, sido, gugun, lat, lng, price_from AS priceFrom,
                 rating, thumbnail_url AS thumbnailUrl, description,
                 ST_Distance_Sphere(location, ST_SRID(POINT(:lng, :lat), 4326)) AS distance
          FROM accommodations
          WHERE ST_Distance_Sphere(location, ST_SRID(POINT(:lng, :lat), 4326)) <= :radiusMeters
          ORDER BY distance
          LIMIT :limit
          """)
  List<AccommodationWithDistance> findNearby(
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("radiusMeters") int radiusMeters,
      @Param("limit") int limit);

  /**
   * Distinct (sido, gugun) 페어 조회 — `/api/accommodations/regions` 응답 빌드용.
   *
   * <p>JPQL의 {@code constructor expression} 대신 native projection({@code Object[]})으로 받아 서비스 레이어에서
   * 그룹화한다. 행정구역 한글 사전순 정렬.
   */
  @Query(
      """
      SELECT DISTINCT a.sido, a.gugun
      FROM Accommodation a
      WHERE a.sido <> '' AND a.gugun <> ''
      ORDER BY a.sido, a.gugun
      """)
  List<Object[]> findDistinctSidoGugunPairs();
}

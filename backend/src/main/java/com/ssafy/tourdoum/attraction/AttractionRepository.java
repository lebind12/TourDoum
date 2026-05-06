package com.ssafy.tourdoum.attraction;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 여행지 레포지토리. */
public interface AttractionRepository extends JpaRepository<Attraction, Long> {

  Page<Attraction> findByRegion(String region, Pageable pageable);

  Page<Attraction> findByCategory(AttractionCategory category, Pageable pageable);

  /**
   * 주어진 좌표로부터 반경 내 여행지를 거리 순으로 반환.
   *
   * <p>ADR-0002: location 컬럼은 DB GENERATED STORED POINT(lng, lat) SRID 4326. ST_Distance_Sphere 는
   * 미터 단위로 구면 거리를 계산한다. H2는 이 함수를 지원하지 않으므로 공간 검색은 MySQL Testcontainers 통합 테스트에서 검증한다.
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
          SELECT *,
                 ST_Distance_Sphere(location, ST_SRID(POINT(:lng, :lat), 4326)) AS distance
          FROM attractions
          WHERE ST_Distance_Sphere(location, ST_SRID(POINT(:lng, :lat), 4326)) <= :radiusMeters
          ORDER BY distance
          LIMIT :limit
          """)
  List<Attraction> findNearby(
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("radiusMeters") int radiusMeters,
      @Param("limit") int limit);
}

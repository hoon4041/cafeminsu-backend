package com.cafeminsu.domain.store.repository;

import com.cafeminsu.domain.store.entity.Store;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    /**
     * 이름 또는 주소에 키워드가 포함된 매장 검색.
     * @SQLRestriction이 자동으로 deleted_at IS NULL을 붙여줌.
     */
    @Query("""
            SELECT s FROM Store s
            WHERE s.name    LIKE CONCAT('%', :keyword, '%')
               OR s.address LIKE CONCAT('%', :keyword, '%')
            ORDER BY s.id DESC
            """)
    Page<Store> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /** 키워드 없을 때 전체 목록 */
    Page<Store> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 주변 매장 — Haversine 기반의 MySQL 공간함수 ST_Distance_Sphere 사용.
     * 단위: 미터.
     *
     * Native query라 @SQLRestriction 자동 적용이 안 되니까 deleted_at 조건 직접 추가.
     * Spring Data가 Object[]로 반환 (id, name, image_url, distance).
     */
    @Query(value = """
            SELECT s.id          AS id,
                   s.name        AS name,
                   s.image_url   AS imageUrl,
                   ST_Distance_Sphere(
                       POINT(s.longitude, s.latitude),
                       POINT(:lng, :lat)
                   ) AS distance
            FROM stores s
            WHERE s.deleted_at IS NULL
              AND s.latitude IS NOT NULL
              AND s.longitude IS NOT NULL
              AND ST_Distance_Sphere(
                       POINT(s.longitude, s.latitude),
                       POINT(:lng, :lat)
                  ) <= :radiusMeters
            ORDER BY distance ASC
            """, nativeQuery = true)
    List<NearbyStoreProjection> findNearby(
            @Param("lat") BigDecimal latitude,
            @Param("lng") BigDecimal longitude,
            @Param("radiusMeters") double radiusMeters
    );

    /** 점주가 운영하는 매장 목록 (체인 가능) */
    List<Store> findAllByOwnerIdOrderByIdDesc(Long ownerId);

    /**
     * Native query 결과 매핑용 인터페이스 (interface-based projection).
     * Spring Data가 컬럼 alias를 메서드명에 자동 매핑.
     */
    interface NearbyStoreProjection {
        Long getId();
        String getName();
        String getImageUrl();
        Double getDistance();
    }
}

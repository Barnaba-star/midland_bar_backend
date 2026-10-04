package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Projection.BarProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
/*
 * The low-stock level from Settings > Config counts packs: at 5, a 24-crate
 * product is low from 120 bottles down, one sold singly from 5 units down.
 */
public interface BarServiceRepository extends JpaRepository<BarServiceEntity, String> {
    @Query("SELECT s FROM BarServiceEntity s WHERE s.stockSourceUid = :stockItemUid AND s.branchUid = :branchUID")
    List<BarServiceEntity> findDrawingOn(@Param("stockItemUid") String stockItemUid, @Param("branchUID") String branchUID);

    /** Services that draw on a stock item - it cannot be deleted while any do. */
    @Query("SELECT COUNT(s) FROM BarServiceEntity s WHERE s.stockSourceUid = :stockItemUid AND s.branchUid = :branchUID")
    long countDrawingOn(@Param("stockItemUid") String stockItemUid, @Param("branchUID") String branchUID);

    /** Codes already taken in a branch under one prefix, for picking the next one. */
    // Native so deleted rows count too: a code, once given, is never handed
    // to something else, even after the first holder is deleted.
    @Query(value = "SELECT s.service_code FROM bar_services s WHERE s.branch_uid = :branchUID AND s.service_code LIKE CONCAT(:prefix, '-%')", nativeQuery = true)
    List<String> findCodesByPrefix(@Param("branchUID") String branchUID, @Param("prefix") String prefix);

    /** The same lookup, holding a row lock until the transaction ends - for changing the stock count. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BarServiceEntity s WHERE s.uid=:barServiceUID AND s.branchUid=:branchUID")
    Optional<BarServiceEntity> findForUpdate(@Param("barServiceUID") String barServiceUID, @Param("branchUID") String branchUID);

    @Query("SELECT s FROM BarServiceEntity s WHERE s.uid=:barServiceUID AND s.branchUid=:branchUID")
    Optional<BarServiceEntity> findBarServiceByUID(@Param("barServiceUID") String barServiceUID, String branchUID);

    @Query("""
           SELECT s.uid AS uid, s.serviceName AS serviceName,
              s.serviceCode AS serviceCode,
              s.description AS description,
              s.price AS price,
              s.category AS category,
              s.unit AS unit,
              s.packUnit AS packUnit,
              s.unitsPerPack AS unitsPerPack,
              s.buyingPrice AS buyingPrice,
              s.stockQuantity AS stockQuantity,
              CASE WHEN s.trackStock = true
                        AND COALESCE(s.stockQuantity, 0) <= :lowStockLevel * COALESCE(s.unitsPerPack, 1)
                   THEN true ELSE false END AS lowStock,
              s.trackStock AS trackStock,
              s.kind AS kind,
              s.stockSourceUid AS stockSourceUid,
              s.unitsPerSale AS unitsPerSale,
              s.unitLadder AS unitLadder,
              s.saleUnitName AS saleUnitName,
              s.saleUnitCount AS saleUnitCount,
              src.unitLadder AS sourceUnitLadder,
              src.serviceName AS stockSourceName,
              src.stockQuantity AS sourceStockQuantity,
              s.status AS status,
              s.usageType AS usageType
           FROM BarServiceEntity s
           LEFT JOIN BarServiceEntity src ON src.uid = s.stockSourceUid
           WHERE s.branchUid = :branchUID
           ORDER BY s.category, s.serviceName
           """)
    List<BarProjection> findAllBarServiceList(
            @Param("branchUID") String branchUID,
            @Param("lowStockLevel") int lowStockLevel
    );

    @Query(value = """
           SELECT s.uid AS uid, s.serviceName AS serviceName,
              s.serviceCode AS serviceCode,
              s.description AS description,
              s.price AS price,
              s.category AS category,
              s.unit AS unit,
              s.packUnit AS packUnit,
              s.unitsPerPack AS unitsPerPack,
              s.buyingPrice AS buyingPrice,
              s.stockQuantity AS stockQuantity,
              CASE WHEN s.trackStock = true
                        AND COALESCE(s.stockQuantity, 0) <= :lowStockLevel * COALESCE(s.unitsPerPack, 1)
                   THEN true ELSE false END AS lowStock,
              s.trackStock AS trackStock,
              s.kind AS kind,
              s.stockSourceUid AS stockSourceUid,
              s.unitsPerSale AS unitsPerSale,
              s.unitLadder AS unitLadder,
              s.saleUnitName AS saleUnitName,
              s.saleUnitCount AS saleUnitCount,
              src.unitLadder AS sourceUnitLadder,
              src.serviceName AS stockSourceName,
              src.stockQuantity AS sourceStockQuantity,
              s.status AS status,
              s.usageType AS usageType
           FROM BarServiceEntity s
           LEFT JOIN BarServiceEntity src ON src.uid = s.stockSourceUid
           WHERE s.branchUid = :branchUID
             AND (LOWER(s.serviceName) LIKE :search ESCAPE '\\'
                  OR LOWER(COALESCE(s.serviceCode, '')) LIKE :search ESCAPE '\\'
                  OR LOWER(COALESCE(s.description, '')) LIKE :search ESCAPE '\\')
             AND (:countedOnly = false OR s.trackStock = true)
           ORDER BY s.category, s.serviceName
           """, countQuery = """
           SELECT COUNT(s) FROM BarServiceEntity s
           WHERE s.branchUid = :branchUID
             AND (LOWER(s.serviceName) LIKE :search ESCAPE '\\'
                  OR LOWER(COALESCE(s.serviceCode, '')) LIKE :search ESCAPE '\\'
                  OR LOWER(COALESCE(s.description, '')) LIKE :search ESCAPE '\\')
             AND (:countedOnly = false OR s.trackStock = true)
           """)
    Page<BarProjection> findBarServicePage(
            Pageable pageable, @Param("branchUID") String branchUID,
            @Param("lowStockLevel") int lowStockLevel,
            @Param("search") String search,
            @Param("countedOnly") boolean countedOnly
    );

    /** Everything counted in the store - what a stock take goes through. */
    @Query("SELECT s FROM BarServiceEntity s WHERE s.branchUid = :branchUID AND s.isActive = true AND s.trackStock = true ORDER BY s.serviceName")
    java.util.List<BarServiceEntity> findCountable(@Param("branchUID") String branchUID);
}

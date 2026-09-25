package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Model.StoreOpen;
import com.midland.bar.Bar.Projection.BarProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OpenStoreRepository extends JpaRepository<StoreOpen, String> {
        @Query("""
        SELECT COUNT(so)
        FROM StoreOpen so
        LEFT JOIN so.store s
        WHERE s.nameOfStore = :nameOfStore
          AND s.branchUid = :branchUid
    """)
        Long countByStoreNameAndBranchUid(@Param("nameOfStore") String nameOfStore, @Param("branchUid") String branchUid);


        @Query("""
    SELECT
        o.uid AS uid,
        o.openStoreCode AS openStoreCode,
        o.openQuantity AS openQuantity,
        o.updatedAt AS closedDate,
        o.createdAt AS openedDate,
        o.status AS status,

        o.store.uid AS storeUid,
        o.store.nameOfStore AS nameOfStore,
        o.store.codeOfStore AS codeOfStore,
        o.store.quantity AS quantity,
        o.store.description AS description,
        o.store.status AS storeStatus,
        o.store.buyingPrice AS buyingPrice,
        o.store.totalQuantityPrice AS totalQuantityPrice,
        o.store.usedQuantity AS usedQuantity,
        o.store.notUsedQuantity AS notUsedQuantity,

        o.store.barServiceEntity.uid AS barServiceEntityUid

    FROM StoreOpen o
    WHERE o.store.uid = :branchUID
      AND o.status = 'OPEN'
""")
        List<BarProjection> findOpenStoreList(
                @Param("branchUID") String branchUID
        );

        @Query("""
    SELECT
        o.uid AS uid,
        o.openStoreCode AS openStoreCode,
        o.openQuantity AS openQuantity,
        o.updatedAt AS closedDate,
        o.createdAt AS openedDate,
        o.status AS status,
        o.store.uid AS storeUid,
        o.store.nameOfStore AS nameOfStore,
        o.store.codeOfStore AS codeOfStore,
        o.store.quantity AS quantity,
        o.store.description AS description,
        o.store.status AS storeStatus,
        o.store.buyingPrice AS buyingPrice,
        o.store.totalQuantityPrice AS totalQuantityPrice,
        o.store.usedQuantity AS usedQuantity,
        o.store.notUsedQuantity AS notUsedQuantity,
        o.store.barServiceEntity.serviceName AS serviceName,
        o.store.barServiceEntity.uid AS barServiceEntityUID
    FROM StoreOpen o
    WHERE o.branchUid = :branchUID
      AND o.status = :searchKey
""")
        Page<BarProjection> findOpenStorePage(Pageable pageable, @Param("branchUID") String branchUID, String searchKey);

        @Query("""
    SELECT
        o.store.barServiceEntity.uid as barServiceEntityUID,
        o.store.nameOfStore as nameOfStore,
        o.openStoreCode AS openStoreCode,
        o.store.barServiceEntity.serviceName AS serviceName,
        o.createdAt AS openedDate,
        o.updatedAt AS closedDate,
        o.status AS status
    FROM StoreOpen o
    WHERE o.store.branchUid = :branchUID
      AND o.status = :status
      AND o.store.barServiceEntity.uid IS NOT NULL
""")
        List<BarProjection> findServiceEntityUIDList(
                @Param("branchUID") String branchUID,
                @Param("status") String status
        );

        @Query("""
    SELECT o
    FROM StoreOpen o
    WHERE o.branchUid = :branchUID
      AND o.status = 'OPEN'
      AND o.store.barServiceEntity = :barServiceEntity
""")
        List<StoreOpen> findOpenStoreListByService(
                @Param("branchUID") String branchUID,
                @Param("barServiceEntity") BarServiceEntity barServiceEntity
        );

        // The open stores behind every service on one sale, in a single select.
        @Query("""
    SELECT o
    FROM StoreOpen o
    JOIN FETCH o.store s
    WHERE o.branchUid = :branchUID
      AND o.status = 'OPEN'
      AND s.barServiceEntity.uid IN :serviceUids
""")
        List<StoreOpen> findOpenStoreListByServices(
                @Param("branchUID") String branchUID,
                @Param("serviceUids") Collection<String> serviceUids
        );





        @Query(value = """
    SELECT so.*
    FROM store_open so
    INNER JOIN store s
        ON s.uid = so.store_uid
    INNER JOIN bar_services ss
        ON ss.uid = s.bar_service_entity_uid
    WHERE so.branch_uid = :branchUID
      AND so.status = 'OPEN'
      AND ss.uid = :serviceUID
    LIMIT 1
    """, nativeQuery = true)
        Optional<StoreOpen> findCurrentUsageStore(
                @Param("branchUID") String branchUID,
                @Param("serviceUID") String serviceUID
        );

        @Query("SELECT COUNT(o) FROM StoreOpen o WHERE o.branchUid = :branchUID AND o.status = 'OPEN'")
        long countOpenStores(@Param("branchUID") String branchUID);
}

package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Projection.ServiceCommissionProjection;

import com.midland.bar.Bar.Model.Commission;
import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Projection.CommissionProjection;
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
public interface CommissionRepository extends JpaRepository<Commission, String> {

    @Query("SELECT c FROM Commission c WHERE c.uid=:commissionUID AND c.branchUid=:branchUID AND c.isActive=true")
    Optional<Commission> findCommissionByUID(String commissionUID, String branchUID);

    @Query("""
            SELECT
                c.uid as uid,
                c.traPercent as traPercent, c.ownerPercent as ownerPercent, c.staffPercent as staffPercent, c.emergencyPercent as emergencyPercent,
                c.totalPercent as totalPercent, c.maintenancePercent as maintenancePercent, c.otherPercent as otherPercent,
                s.serviceName as serviceName, s.price as price, c.loanPercent as loanPercent, c.rentPercent as rentPercent, c.waterPercent as waterPercent,
                c.lukuPercent as lukuPercent, c.stockPurchasePercent as stockPurchasePercent
                FROM Commission c LEFT JOIN c.barService s
                WHERE c.isActive=true AND c.branchUid=:branchUID
            """)
    List<CommissionProjection> findCommissionList(String branchUID);

    @Query("""
            SELECT
                c.uid as uid,
                c.traPercent as traPercent, c.ownerPercent as ownerPercent, c.staffPercent as staffPercent, c.emergencyPercent as emergencyPercent,
                c.totalPercent as totalPercent, c.maintenancePercent as maintenancePercent, c.otherPercent as otherPercent,
                s.serviceName as serviceName, s.price as price, c.loanPercent as loanPercent, c.rentPercent as rentPercent, c.waterPercent as waterPercent,
                c.lukuPercent as lukuPercent, c.stockPurchasePercent as stockPurchasePercent
                FROM Commission c LEFT JOIN c.barService s
                WHERE c.isActive=true AND c.branchUid=:branchUID
            """)
    Page<CommissionProjection> findCommissionPage(Pageable pageable, String branchUID);

    /*
     * Every service in the branch, joined to its commission when it has
     * one - the Setting > Commission list, where the unconfigured ones are
     * the point of looking.
     */
    @Query(value = """
            SELECT s.uid AS serviceUid, s.serviceName AS serviceName, s.serviceCode AS serviceCode,
                   s.category AS category, s.price AS price,
                   c.uid AS commissionUid,
                   c.staffPercent AS staffPercent, c.ownerPercent AS ownerPercent, c.traPercent AS traPercent,
                   c.maintenancePercent AS maintenancePercent, c.emergencyPercent AS emergencyPercent,
                   c.otherPercent AS otherPercent, c.rentPercent AS rentPercent, c.loanPercent AS loanPercent,
                   c.lukuPercent AS lukuPercent, c.waterPercent AS waterPercent,
                   c.stockPurchasePercent AS stockPurchasePercent, c.totalPercent AS totalPercent
            FROM BarServiceEntity s
            LEFT JOIN Commission c ON c.barService = s AND c.isActive = true AND c.branchUid = s.branchUid
            WHERE s.branchUid = :branchUID
              AND (s.kind IS NULL OR s.kind <> 'STOCK_ITEM')
              AND (LOWER(s.serviceName) LIKE :search ESCAPE '\\'
                   OR LOWER(COALESCE(s.serviceCode, '')) LIKE :search ESCAPE '\\'
                   OR LOWER(COALESCE(s.description, '')) LIKE :search ESCAPE '\\')
            ORDER BY s.serviceName
            """,
            countQuery = """
            SELECT COUNT(s) FROM BarServiceEntity s
            WHERE s.branchUid = :branchUID
              AND (s.kind IS NULL OR s.kind <> 'STOCK_ITEM')
              AND (LOWER(s.serviceName) LIKE :search ESCAPE '\\'
                   OR LOWER(COALESCE(s.serviceCode, '')) LIKE :search ESCAPE '\\'
                   OR LOWER(COALESCE(s.description, '')) LIKE :search ESCAPE '\\')
            """)
    Page<ServiceCommissionProjection> findServiceCommissionPage(@Param("branchUID") String branchUID,
                                                                @Param("search") String search,
                                                                Pageable pageable);

    @Query("""
            SELECT c FROM Commission c WHERE c.barService=:service AND c.branchUid=:branchUID
            """)
    Optional<Commission> findCommissionByService(BarServiceEntity service, String branchUID);

    // Every service on one sale in a single select. Recording a sale used to
    // look each commission up on its own, three separate times over.
    @Query("""
            SELECT c FROM Commission c
            LEFT JOIN FETCH c.barService s
            WHERE c.branchUid=:branchUID AND s.uid IN :serviceUids
            """)
    List<Commission> findCommissionsByServices(
            @Param("serviceUids") Collection<String> serviceUids,
            @Param("branchUID") String branchUID
    );
}

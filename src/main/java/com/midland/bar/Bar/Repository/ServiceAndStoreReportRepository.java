package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.ServiceAndStoreReport;
import com.midland.bar.Bar.Projection.CommissionTotalProjection;
import com.midland.bar.Bar.Projection.BarProjection;
import com.midland.bar.Bar.Projection.StoreReportSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Repository
public interface ServiceAndStoreReportRepository extends JpaRepository<ServiceAndStoreReport, String> {
    @Query("""
    SELECT
        sas.storeOpen.uid AS storeOpenUid,

        sas.storeOpen.openStoreCode AS openStoreCode,

        sas.storeOpen.createdAt AS openedDate,

        sas.storeOpen.updatedAt AS closedDate,

        sas.storeOpen.status AS status,

        sas.storeOpen.store.buyingPrice AS buyingPrice,

        sas.storeOpen.store.barServiceEntity.serviceName AS serviceName,
        
        COALESCE(SUM(sas.sharedAmount), 0)
            AS sharedAmount,

        COALESCE(SUM(sas.barReports.traAmount), 0)
            AS traAmount,

        COALESCE(SUM(sas.barReports.emergencyAmount), 0)
            AS emergencyAmount,

        COALESCE(SUM(sas.barReports.staffAmount), 0)
            AS staffAmount,

        COALESCE(SUM(sas.barReports.othersAmount), 0)
            AS othersAmount,

        COALESCE(SUM(sas.barReports.ownerAmount), 0)
            AS ownerAmount,
        COALESCE(SUM(sas.barReports.rentAmount), 0)
            AS rentAmount,
        COALESCE(SUM(sas.barReports.loanAmount), 0)
            AS loanAmount,
        COALESCE(SUM(sas.barReports.waterAmount), 0)
            AS waterAmount,
        COALESCE(SUM(sas.barReports.lukuAmount), 0)
            AS lukuAmount,
        COALESCE(SUM(sas.barReports.stockPurchaseAmount), 0)
            AS stockPurchaseAmount,
        (
            COALESCE(SUM(sas.barReports.traAmount), 0)
            + COALESCE(SUM(sas.barReports.emergencyAmount), 0)
            + COALESCE(SUM(sas.barReports.staffAmount), 0)
            + COALESCE(SUM(sas.barReports.othersAmount), 0)
            + COALESCE(SUM(sas.barReports.ownerAmount), 0)
            + COALESCE(SUM(sas.barReports.rentAmount), 0)
            + COALESCE(SUM(sas.barReports.loanAmount), 0)
            + COALESCE(SUM(sas.barReports.waterAmount), 0)
            + COALESCE(SUM(sas.barReports.lukuAmount), 0)
            + COALESCE(SUM(sas.barReports.stockPurchaseAmount), 0)
            + COALESCE(SUM(sas.barReports.maintenanceAmount), 0)
        ) AS totalPrice,

        COALESCE(SUM(sas.barReports.maintenanceAmount), 0)
            AS maintenanceAmount,

        COUNT(sas.uid) AS reportCount

    FROM ServiceAndStoreReport sas

    WHERE sas.branchUid = :branchUID

    AND sas.storeOpen.status = 'CLOSED'

    AND sas.storeOpen.updatedAt >= :startDate

    AND sas.storeOpen.updatedAt < :endDate

    GROUP BY
        sas.storeOpen.uid,
        sas.storeOpen.openStoreCode,
        sas.storeOpen.createdAt,
        sas.storeOpen.updatedAt,
        sas.storeOpen.status,
        sas.storeOpen.store.buyingPrice,
        sas.storeOpen.store.barServiceEntity.serviceName

    ORDER BY sas.storeOpen.updatedAt DESC
""")
    Page<StoreReportSummaryProjection> findStoreReportSummaryPage(
            Pageable pageable,
            @Param("branchUID") String branchUID,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


}

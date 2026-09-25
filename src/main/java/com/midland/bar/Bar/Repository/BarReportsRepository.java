package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BarReports;
import com.midland.bar.Bar.Projection.RevenueTrendProjection;
import com.midland.bar.Bar.Projection.StaffEarningsProjection;
import com.midland.bar.Bar.Projection.BarProjection;
import com.midland.bar.Bar.Projection.BarServiceRevenueProjection;
import com.midland.bar.Utils.Responses.Response;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BarReportsRepository extends JpaRepository<BarReports, String> {
    @Query("""
            SELECT r FROM BarReports r WHERE r.uid=:barReportUID AND r.branchUid=:branchUID
            """)
    Optional<BarReports> findBarReportByUID(String barReportUID, String branchUID);

    @Query("""
            SELECT r.uid as uid, r.traAmount as traAmount, r.ownerAmount as ownerAmount, r.staffAmount as staffAmount,
            r.emergencyAmount as emergencyAmount, r.othersAmount as othersAmount, r.maintenanceAmount as maintenanceAmount,
            sls.paymentMethod as paymentMethod, r.rentAmount as rentAmount,
           r.loanAmount as loanAmount,
           r.waterAmount as waterAmount,
           r.lukuAmount as lukuAmount,
           r.stockPurchaseAmount as stockPurchaseAmount,
            stf.firstName as firstName, stf.middleName as middleName, stf.lastName as lastName,
            ssv.serviceName as serviceName, ssv.serviceCode as serviceCode, ssv.price as price
            FROM BarReports r LEFT JOIN r.barStaff stf LEFT JOIN r.barServiceEntity ssv LEFT JOIN r.barSales sls
            WHERE r.branchUid=:branchUID AND r.isActive=true
            """)
    List<BarReports> findBarReportsList(String branchUID);

    @Query("""
            SELECT r.uid as uid, r.traAmount as traAmount, r.ownerAmount as ownerAmount, r.staffAmount as staffAmount,
            r.emergencyAmount as emergencyAmount, r.othersAmount as othersAmount, r.maintenanceAmount as maintenanceAmount,
            sls.paymentMethod as paymentMethod, r.rentAmount as rentAmount,
           r.loanAmount as loanAmount,
           r.waterAmount as waterAmount,
           r.lukuAmount as lukuAmount,
           r.stockPurchaseAmount as stockPurchaseAmount,
            stf.firstName as firstName, stf.middleName as middleName, stf.lastName as lastName,
            ssv.serviceName as serviceName, ssv.serviceCode as serviceCode, ssv.price as price
            FROM BarReports r LEFT JOIN r.barStaff stf LEFT JOIN r.barServiceEntity ssv LEFT JOIN r.barSales sls
            WHERE r.branchUid=:branchUID AND r.isActive=true AND r.createdAt = :date
            """)
    Page<BarProjection> findBarReportsPage(Pageable pageable, String branchUID, LocalDate date);

    @Query("""
            SELECT  SUM(r.traAmount) as traAmount, SUM(r.ownerAmount) as ownerAmount, SUM(r.staffAmount) as staffAmount,
            SUM(r.emergencyAmount) as emergencyAmount, SUM(r.othersAmount) as othersAmount, SUM(r.maintenanceAmount) as maintenanceAmount,
            SUM(r.rentAmount) as rentAmount, SUM(r.loanAmount) as loanAmount, SUM(r.lukuAmount) as lukuAmount, SUM(r.waterAmount) as waterAmount,
            SUM(r.stockPurchaseAmount) as stockPurchaseAmount
            FROM BarReports r WHERE r.branchUid=:branchUID AND r.isActive=true AND r.createdAt = :date
            """)
    Optional<BarProjection> findBarRevenueReport(String branchUID, LocalDate date);

    @Query("""
        SELECT
            SUM(COALESCE(r.traAmount, 0)) as traAmount,
            SUM(COALESCE(r.ownerAmount, 0)) as ownerAmount,
            SUM(COALESCE(r.staffAmount, 0)) as staffAmount,
            SUM(COALESCE(r.emergencyAmount, 0)) as emergencyAmount,
            SUM(COALESCE(r.othersAmount, 0)) as othersAmount,
            SUM(COALESCE(r.loanAmount, 0)) as loanAmount,
        SUM(COALESCE(r.rentAmount, 0)) as rentAmount,
        SUM(COALESCE(r.waterAmount, 0)) as waterAmount,
        SUM(COALESCE(r.lukuAmount, 0)) as lukuAmount,
        SUM(COALESCE(r.stockPurchaseAmount, 0)) as stockPurchaseAmount,
            SUM(COALESCE(r.maintenanceAmount, 0)) as maintenanceAmount
        FROM BarReports r
        WHERE r.branchUid = :branchUID
          AND r.isActive = true
          AND r.createdAt >= :startDate
          AND r.createdAt < :endDate
        """)
    Optional<BarProjection> findCurrentBarRevenueReport(
            String branchUID,
            LocalDate startDate,
            LocalDate endDate
    );


    @Query("""
    SELECT
        ssv.uid as serviceUID,
        ssv.serviceName as serviceName,
        ssv.serviceCode as serviceCode,

        SUM(COALESCE(r.traAmount, 0)) as traAmount,
        SUM(COALESCE(r.ownerAmount, 0)) as ownerAmount,
        SUM(COALESCE(r.staffAmount, 0)) as staffAmount,
        SUM(COALESCE(r.emergencyAmount, 0)) as emergencyAmount,
        SUM(COALESCE(r.othersAmount, 0)) as othersAmount,
        SUM(COALESCE(r.loanAmount, 0)) as loanAmount,
        SUM(COALESCE(r.rentAmount, 0)) as rentAmount,
        SUM(COALESCE(r.waterAmount, 0)) as waterAmount,
        SUM(COALESCE(r.lukuAmount, 0)) as lukuAmount,
        SUM(COALESCE(r.stockPurchaseAmount, 0)) as stockPurchaseAmount,
        SUM(COALESCE(r.maintenanceAmount, 0)) as maintenanceAmount,

        SUM(
            COALESCE(r.traAmount, 0) +
            COALESCE(r.ownerAmount, 0) +
            COALESCE(r.staffAmount, 0) +
            COALESCE(r.emergencyAmount, 0) +
            COALESCE(r.othersAmount, 0) +
            COALESCE(r.rentAmount, 0) +
            COALESCE(r.loanAmount, 0) +
            COALESCE(r.waterAmount, 0) +
            COALESCE(r.lukuAmount, 0) +
            COALESCE(r.stockPurchaseAmount, 0) +
            COALESCE(r.maintenanceAmount, 0)
        ) as totalAmount

    FROM BarReports r

    LEFT JOIN r.barServiceEntity ssv

    WHERE r.branchUid = :branchUID
      AND r.isActive = true
      AND r.createdAt = :date

    GROUP BY
        ssv.uid,
        ssv.serviceName,
        ssv.serviceCode

    ORDER BY
        ssv.serviceName ASC
    """)
    List<BarServiceRevenueProjection> findBarRevenueByService(
            String branchUID,
            LocalDate date
    );

    @Query("""
    SELECT r.uid as uid,
           r.traAmount as traAmount,
           r.ownerAmount as ownerAmount,
           r.staffAmount as staffAmount,
           r.emergencyAmount as emergencyAmount,
           r.othersAmount as othersAmount,
           r.maintenanceAmount as maintenanceAmount,
           r.rentAmount as rentAmount,
           r.loanAmount as loanAmount,
           r.waterAmount as waterAmount,
           r.lukuAmount as lukuAmount,
           r.stockPurchaseAmount as stockPurchaseAmount,
           sls.paymentMethod as paymentMethod,
           stf.firstName as firstName,
           stf.middleName as middleName,
           stf.lastName as lastName,
           ssv.serviceName as serviceName,
           ssv.serviceCode as serviceCode,
           ssv.price as price
    FROM BarReports r
    LEFT JOIN r.barStaff stf
    LEFT JOIN r.barServiceEntity ssv
    LEFT JOIN r.barSales sls
    WHERE r.branchUid = :branchUID
      AND r.isActive = true
      AND r.createdAt >= :startDate
      AND r.createdAt < :endDate
    """)
    Page<BarProjection> findCurrentBarReportsPage(
            Pageable pageable,
            String branchUID,
            LocalDate startDate,
            LocalDate endDate
    );

    @Query("""
    SELECT
        ssv.uid as serviceUID,
        ssv.serviceName as serviceName,
        ssv.serviceCode as serviceCode,

        SUM(COALESCE(r.traAmount, 0)) as traAmount,
        SUM(COALESCE(r.ownerAmount, 0)) as ownerAmount,
        SUM(COALESCE(r.staffAmount, 0)) as staffAmount,
        SUM(COALESCE(r.emergencyAmount, 0)) as emergencyAmount,
        SUM(COALESCE(r.othersAmount, 0)) as othersAmount,
        SUM(COALESCE(r.loanAmount, 0)) as loanAmount,
        SUM(COALESCE(r.rentAmount, 0)) as rentAmount,
        SUM(COALESCE(r.waterAmount, 0)) as waterAmount,
        SUM(COALESCE(r.lukuAmount, 0)) as lukuAmount,
        SUM(COALESCE(r.stockPurchaseAmount, 0)) as stockPurchaseAmount,
        SUM(COALESCE(r.maintenanceAmount, 0)) as maintenanceAmount,

        SUM(
            COALESCE(r.traAmount, 0) +
            COALESCE(r.ownerAmount, 0) +
            COALESCE(r.staffAmount, 0) +
            COALESCE(r.emergencyAmount, 0) +
            COALESCE(r.othersAmount, 0) +
            COALESCE(r.rentAmount, 0) +
            COALESCE(r.loanAmount, 0) +
            COALESCE(r.waterAmount, 0) +
            COALESCE(r.lukuAmount, 0) +
            COALESCE(r.stockPurchaseAmount, 0) +
            COALESCE(r.maintenanceAmount, 0)
        ) as totalAmount

    FROM BarReports r

    LEFT JOIN r.barServiceEntity ssv

    WHERE r.branchUid = :branchUID
      AND r.isActive = true
      AND r.createdAt >= :startDate
      AND r.createdAt < :endDate

    GROUP BY
        ssv.uid,
        ssv.serviceName,
        ssv.serviceCode

    ORDER BY
        ssv.serviceName ASC
    """)
    List<BarServiceRevenueProjection> findCurrentBarRevenueByService(
            String branchUID,
            LocalDate startDate,
            LocalDate endDate
    );


    @Query("""
    SELECT
        ssv.uid as serviceUID,
        ssv.serviceName as serviceName,
        ssv.serviceCode as serviceCode,

        SUM(COALESCE(r.traAmount, 0)) as traAmount,
        SUM(COALESCE(r.ownerAmount, 0)) as ownerAmount,
        SUM(COALESCE(r.staffAmount, 0)) as staffAmount,
        SUM(COALESCE(r.emergencyAmount, 0)) as emergencyAmount,
        SUM(COALESCE(r.othersAmount, 0)) as othersAmount,
        SUM(COALESCE(r.maintenanceAmount, 0)) as maintenanceAmount,
        SUM(COALESCE(r.loanAmount, 0)) as loanAmount,
        SUM(COALESCE(r.rentAmount, 0)) as rentAmount,
        SUM(COALESCE(r.waterAmount, 0)) as waterAmount,
        SUM(COALESCE(r.lukuAmount, 0)) as lukuAmount,
        SUM(COALESCE(r.stockPurchaseAmount, 0)) as stockPurchaseAmount,
        SUM(
            COALESCE(r.traAmount, 0) +
            COALESCE(r.ownerAmount, 0) +
            COALESCE(r.staffAmount, 0) +
            COALESCE(r.emergencyAmount, 0) +
            COALESCE(r.othersAmount, 0) +
            COALESCE(r.rentAmount, 0) +
            COALESCE(r.loanAmount, 0) +
            COALESCE(r.waterAmount, 0) +
            COALESCE(r.lukuAmount, 0) +
            COALESCE(r.stockPurchaseAmount, 0) +
            COALESCE(r.maintenanceAmount, 0)
        ) as totalAmount
    FROM BarReports r
    LEFT JOIN r.barServiceEntity ssv
    WHERE r.branchUid = :branchUID
      AND r.isActive = true
      AND r.createdAt >= :startDate
      AND r.createdAt < :endDate
      AND ssv.uid IN :serviceEntityUID
    GROUP BY
        ssv.uid,
        ssv.serviceName,
        ssv.serviceCode
    ORDER BY
        ssv.serviceName ASC
""")
    Page<BarServiceRevenueProjection> findBarRevenueByStore(
            Pageable pageable,
            @Param("serviceEntityUID") List<String> serviceEntityUID,
            @Param("branchUID") String branchUID,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // A service's price is split eleven ways across these columns, so the
    // branch's revenue is their sum - the same total the revenue report shows.
    @Query("""
            SELECT COALESCE(SUM(COALESCE(r.traAmount,0) + COALESCE(r.ownerAmount,0) + COALESCE(r.staffAmount,0)
                 + COALESCE(r.emergencyAmount,0) + COALESCE(r.othersAmount,0) + COALESCE(r.loanAmount,0)
                 + COALESCE(r.rentAmount,0) + COALESCE(r.waterAmount,0) + COALESCE(r.lukuAmount,0)
                 + COALESCE(r.stockPurchaseAmount,0) + COALESCE(r.maintenanceAmount,0)), 0)
            FROM BarReports r
            WHERE r.branchUid = :branchUID AND r.createdAt = :date
            """)
    long totalRevenueOn(@Param("branchUID") String branchUID, @Param("date") LocalDate date);

    @Query("SELECT COUNT(r) FROM BarReports r WHERE r.branchUid = :branchUID AND r.createdAt = :date")
    long countServicesSoldOn(@Param("branchUID") String branchUID, @Param("date") LocalDate date);

    // One row per day for the home page's trend line. Days with no sales are
    // simply absent - the caller fills those in as zero so the line stays
    // continuous instead of skipping closed days.
    @Query("""
            SELECT r.createdAt AS date, COALESCE(SUM(COALESCE(r.traAmount,0) + COALESCE(r.ownerAmount,0) + COALESCE(r.staffAmount,0)
                 + COALESCE(r.emergencyAmount,0) + COALESCE(r.othersAmount,0) + COALESCE(r.loanAmount,0)
                 + COALESCE(r.rentAmount,0) + COALESCE(r.waterAmount,0) + COALESCE(r.lukuAmount,0)
                 + COALESCE(r.stockPurchaseAmount,0) + COALESCE(r.maintenanceAmount,0)), 0) AS amount
            FROM BarReports r
            WHERE r.branchUid = :branchUID AND r.createdAt >= :startDate
            GROUP BY r.createdAt
            ORDER BY r.createdAt
            """)
    List<RevenueTrendProjection> revenueTrend(@Param("branchUID") String branchUID, @Param("startDate") LocalDate startDate);

    // Who brought in what. staffAmount is the cut that belongs to the person
    // who did the service, so summing it by staff ranks them by contribution
    // rather than by how many services they happened to touch.
    @Query("""
            SELECT st.uid AS staffUid,
                   st.firstName AS firstName,
                   st.lastName AS lastName,
                   COALESCE(SUM(COALESCE(r.staffAmount, 0)), 0) AS earned,
                   COUNT(r) AS servicesDone
            FROM BarReports r
            LEFT JOIN r.barStaff st
            WHERE r.branchUid = :branchUID
              AND r.createdAt >= :startDate
              AND st.uid IS NOT NULL
            GROUP BY st.uid, st.firstName, st.lastName
            ORDER BY COALESCE(SUM(COALESCE(r.staffAmount, 0)), 0) DESC
            """)
    List<StaffEarningsProjection> staffEarningsSince(@Param("branchUID") String branchUID, @Param("startDate") LocalDate startDate);
}

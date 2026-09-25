package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.StockAndPurchase;
import com.midland.bar.Bar.Projection.StockAndPurchaseProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockAndPurchaseRepository extends JpaRepository<StockAndPurchase, String> {
    @Query("""
    SELECT s
    FROM StockAndPurchase s
    WHERE s.barService.uid = :serviceUid
      AND s.weekDate = :weekDate
      AND s.branchUid = :branchUID
    ORDER BY s.createdAt DESC
""")
    Optional<StockAndPurchase> findCurrentWeekByService(
            @Param("branchUID") String branchUID,
            @Param("serviceUid") String serviceUid,
            @Param("weekDate") LocalDate weekDate
    );

    // This week's rows for every service on one sale, in one select.
    @Query("""
    SELECT s
    FROM StockAndPurchase s
    LEFT JOIN FETCH s.barService svc
    WHERE svc.uid IN :serviceUids
      AND s.weekDate = :weekDate
      AND s.branchUid = :branchUID
    ORDER BY s.createdAt DESC
""")
    List<StockAndPurchase> findCurrentWeekByServices(
            @Param("branchUID") String branchUID,
            @Param("serviceUids") Collection<String> serviceUids,
            @Param("weekDate") LocalDate weekDate
    );
    @Query("""
    SELECT
        s.uid AS uid,
        s.paymentStatus AS paymentStatus,
        s.payedAmount AS payedAmount,
        s.totalAmount AS totalAmount,
        s.remainingAmount AS remainingAmount,
        s.weekDate AS weekDate,
        c.uid AS commissionUid,
        ss.uid AS serviceUid,
        ss.serviceName AS serviceName,
        ss.price AS servicePrice
    FROM StockAndPurchase s
    LEFT JOIN s.commission c
    LEFT JOIN s.barService ss
    WHERE s.branchUid = :branchUID
      AND s.weekDate BETWEEN :startDate AND :endDate
    ORDER BY s.weekDate DESC
""")
    List<StockAndPurchaseProjection> findByWeekDateRange(
            @Param("branchUID") String branchUID,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


    @Query("""
    SELECT
        s.uid AS uid,
        s.paymentStatus AS paymentStatus,
        s.payedAmount AS payedAmount,
        s.totalAmount AS totalAmount,
        s.remainingAmount AS remainingAmount,
        s.weekDate AS weekDate,

        c.uid AS commissionUid,

        ss.uid AS serviceUid,
        ss.serviceName AS serviceName,
        ss.price AS servicePrice,

        d.uid AS descriptionUid,
        d.descriptions AS descriptions,
        d.weekDate AS descriptionWeekDate,
        d.amount AS descriptionAmount

    FROM StockAndPurchase s

    LEFT JOIN s.commission c
    LEFT JOIN s.barService ss
    LEFT JOIN s.descriptions d

    WHERE s.uid = :uid
      AND s.branchUid = :branchUID

    ORDER BY d.createdAt DESC
""")
    List<StockAndPurchaseProjection> findStockPurchaseByUid(
            @Param("uid") String uid,
            @Param("branchUID") String branchUID
    );



}

package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.StockAndPurchaseDescriptions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockAndPurchaseDescriptionsRepository
        extends JpaRepository<StockAndPurchaseDescriptions, String> {

    @Query(value = """
    SELECT d.*
    FROM stock_and_purchase_descriptions d
    INNER JOIN stock_and_purchase s
        ON d.stock_and_purchase = s.uid
    WHERE s.uid = :stockPurchaseUid
      AND d.branch_uid = :branchUID
    ORDER BY d.created_at DESC
    """, nativeQuery = true)
    List<StockAndPurchaseDescriptions> findByStockPurchaseUid(
            @Param("branchUID") String branchUID,
            @Param("stockPurchaseUid") String stockPurchaseUid

    );

    /**
     * [day, description, product, amount] - what was paid out of Manunuzi in
     * [from, to], whichever week's savings it came from; one payment spread
     * over several weeks comes back as one line.
     */
    @Query("SELECT d.createdAt, d.descriptions, s.barService.serviceName, SUM(d.amount) FROM StockAndPurchaseDescriptions d JOIN d.stockAndPurchase s " +
           "WHERE s.branchUid = :b AND d.isActive = true AND d.createdAt BETWEEN :from AND :to " +
           "GROUP BY d.createdAt, d.descriptions, s.barService.serviceName ORDER BY d.createdAt DESC, d.descriptions")
    List<Object[]> spentBetween(@Param("b") String branchUID, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}


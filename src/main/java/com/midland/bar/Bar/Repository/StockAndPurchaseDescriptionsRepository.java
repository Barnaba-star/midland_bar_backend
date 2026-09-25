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



}


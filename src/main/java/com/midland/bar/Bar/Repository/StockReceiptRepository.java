package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.StockReceipt;
import com.midland.bar.Bar.Projection.StockMovementProjection;
import com.midland.bar.Bar.Projection.StockReceiptProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockReceiptRepository extends JpaRepository<StockReceipt, String> {

    /*
     * Every counted service in the branch, with what came in and went out
     * between the two instants. Subqueries rather than joins, so a service
     * with many deliveries and many sales is not multiplied into the sums.
     *
     * Used units are what came out of each store row: a Castle Lite line
     * takes from Castle Lite, a mshikaki line from the beef it is made of
     * (quantity x units per sale). A line from before this was recorded
     * falls back to its own service and quantity.
     *
     * Loss (countLoss*) is everything that left the store unsold except the
     * house's own use: counts found short, spoiled, broken, shrinkage, lost, other.
     */
    @Query(value = """
           SELECT s.uid AS uid, s.serviceName AS serviceName, s.serviceCode AS serviceCode,
                  s.category AS category, s.packUnit AS packUnit, s.unitsPerPack AS unitsPerPack,
                  s.price AS price, s.stockQuantity AS stockQuantity,
                  (SELECT COALESCE(SUM(r.unitsAdded), 0) FROM StockReceipt r
                    WHERE r.barService = s AND r.receivedAt >= :from AND r.receivedAt < :to) AS purchasedUnits,
                  (SELECT COALESCE(SUM(r.totalCost), 0) FROM StockReceipt r
                    WHERE r.barService = s AND r.receivedAt >= :from AND r.receivedAt < :to) AS purchasedCost,
                  (SELECT COALESCE(SUM(COALESCE(b.stockUnits, b.quantity, 1)), 0) FROM BarSales b
                    WHERE (b.stockItemUid = s.uid OR (b.stockItemUid IS NULL AND b.barServiceEntity = s))
                      AND b.createdAt >= :fromDate AND b.createdAt <= :toDate)
                  + (SELECT COALESCE(SUM(x.units), 0) FROM BarSaleStock x
                    WHERE x.stockItemUid = s.uid AND x.soldOn >= :fromDate AND x.soldOn <= :toDate) AS usedUnits,
                  (SELECT COALESCE(SUM(COALESCE(b.lineTotal, COALESCE(b.unitPrice, s.price) * COALESCE(b.quantity, 1))), 0) FROM BarSales b
                    WHERE (b.stockItemUid = s.uid OR (b.stockItemUid IS NULL AND b.barServiceEntity = s))
                      AND b.createdAt >= :fromDate AND b.createdAt <= :toDate) AS soldValue,
                  (SELECT COALESCE(SUM(COALESCE(b.unitCost, 0) * COALESCE(b.quantity, 1)), 0) FROM BarSales b
                    WHERE (b.stockItemUid = s.uid OR (b.stockItemUid IS NULL AND b.barServiceEntity = s))
                      AND b.createdAt >= :fromDate AND b.createdAt <= :toDate)
                  + (SELECT COALESCE(SUM(x.cost), 0) FROM BarSaleStock x
                    WHERE x.stockItemUid = s.uid AND x.soldOn >= :fromDate AND x.soldOn <= :toDate)
                  - (SELECT COALESCE(SUM(x.cost), 0) FROM BarSaleStock x
                    WHERE x.primaryStockItemUid = s.uid AND x.soldOn >= :fromDate AND x.soldOn <= :toDate) AS soldCost,
                  (SELECT COALESCE(SUM(a.unitsChanged), 0) FROM StockAdjustment a
                    WHERE a.barService = s AND a.adjustedAt >= :from AND a.adjustedAt < :to) AS adjustedUnits,
                  (SELECT COALESCE(SUM(a.costValue), 0) FROM StockAdjustment a
                    WHERE a.barService = s AND a.adjustedAt >= :from AND a.adjustedAt < :to) AS adjustedValue,
                  (SELECT COALESCE(SUM(a.unitsChanged), 0) FROM StockAdjustment a
                    WHERE a.barService = s AND COALESCE(a.reason, '') <> 'INTERNAL_USE' AND a.unitsChanged < 0
                      AND a.adjustedAt >= :from AND a.adjustedAt < :to) AS countLossUnits,
                  (SELECT COALESCE(SUM(a.costValue), 0) FROM StockAdjustment a
                    WHERE a.barService = s AND COALESCE(a.reason, '') <> 'INTERNAL_USE' AND a.unitsChanged < 0
                      AND a.adjustedAt >= :from AND a.adjustedAt < :to) AS countLossValue,
                  s.buyingPrice AS buyingPrice, s.unit AS unit, s.unitLadder AS unitLadder
           FROM BarServiceEntity s
           WHERE s.branchUid = :branchUID
             AND s.trackStock = true
             AND (LOWER(s.serviceName) LIKE :search ESCAPE '\\'
                  OR LOWER(COALESCE(s.serviceCode, '')) LIKE :search ESCAPE '\\'
                  OR LOWER(COALESCE(s.description, '')) LIKE :search ESCAPE '\\')
           ORDER BY s.serviceName
           """,
           countQuery = """
           SELECT COUNT(s) FROM BarServiceEntity s
           WHERE s.branchUid = :branchUID
             AND s.trackStock = true
             AND (LOWER(s.serviceName) LIKE :search ESCAPE '\\'
                  OR LOWER(COALESCE(s.serviceCode, '')) LIKE :search ESCAPE '\\'
                  OR LOWER(COALESCE(s.description, '')) LIKE :search ESCAPE '\\')
           """)
    Page<StockMovementProjection> findMovements(@Param("branchUID") String branchUID,
                                                @Param("search") String search,
                                                @Param("from") java.time.LocalDateTime from,
                                                @Param("to") java.time.LocalDateTime to,
                                                @Param("fromDate") java.time.LocalDate fromDate,
                                                @Param("toDate") java.time.LocalDate toDate,
                                                Pageable pageable);

    @Query("""
           SELECT r.uid AS uid, r.packs AS packs, r.looseUnits AS looseUnits,
                  r.unitsPerPack AS unitsPerPack, r.unitsAdded AS unitsAdded,
                  r.packPrice AS packPrice, r.totalCost AS totalCost,
                  r.supplier AS supplier, r.note AS note,
                  r.receivedBy AS receivedBy, r.receivedAt AS receivedAt,
                  r.stockAfter AS stockAfter
           FROM StockReceipt r
           WHERE r.branchUid = :branchUID AND r.barService.uid = :serviceUID
           ORDER BY r.receivedAt DESC
           """)
    Page<StockReceiptProjection> findByService(@Param("branchUID") String branchUID,
                                               @Param("serviceUID") String serviceUID,
                                               Pageable pageable);

    /** What one product was bought at, newest first - with whom and when. */
    @Query("SELECT r FROM StockReceipt r WHERE r.barService.uid = :serviceUid AND r.branchUid = :b ORDER BY r.receivedAt DESC")
    java.util.List<com.midland.bar.Bar.Model.StockReceipt> priceHistory(@org.springframework.data.repository.query.Param("serviceUid") String serviceUid,
                                                                       @org.springframework.data.repository.query.Param("b") String branchUID);
}

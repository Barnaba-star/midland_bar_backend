package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BarSales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only queries behind the profit, best-seller, peak-hour and pots-ledger reports. */
public interface InsightRepository extends JpaRepository<BarSales, String> {

    /**
     * Per product over [from, to): uid, name, code, category, units sold, revenue,
     * cost of the lines whose cost is known, and how many lines had no cost.
     */
    @Query("SELECT s.uid, s.serviceName, s.serviceCode, s.category, " +
           "SUM(COALESCE(b.quantity, 1)), SUM(COALESCE(b.lineTotal, 0)), " +
           "SUM(CASE WHEN b.unitCost IS NULL THEN 0 ELSE b.unitCost * COALESCE(b.quantity, 1) END), " +
           "SUM(CASE WHEN b.unitCost IS NULL THEN 1 ELSE 0 END) " +
           "FROM BarSales b JOIN b.barServiceEntity s " +
           "WHERE b.branchUid = :branchUID AND b.isActive = true AND b.createdAt >= :from AND b.createdAt < :to " +
           "GROUP BY s.uid, s.serviceName, s.serviceCode, s.category")
    List<Object[]> marginByProduct(@Param("branchUID") String branchUID, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Products counted in the store that sold nothing in [from, to): uid, name, code, stock, buying price, units per pack, last sale date. */
    @Query("SELECT s.uid, s.serviceName, s.serviceCode, s.stockQuantity, s.buyingPrice, s.unitsPerPack, " +
           "(SELECT MAX(b2.createdAt) FROM BarSales b2 WHERE b2.barServiceEntity = s AND b2.isActive = true) " +
           "FROM BarServiceEntity s WHERE s.branchUid = :branchUID AND s.isActive = true AND s.trackStock = true " +
           "AND COALESCE(s.stockQuantity, 0) > 0 AND NOT EXISTS (SELECT 1 FROM BarSales b WHERE b.barServiceEntity = s " +
           "AND b.isActive = true AND b.createdAt >= :from AND b.createdAt < :to) ORDER BY s.serviceName")
    List<Object[]> slowMovers(@Param("branchUID") String branchUID, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Sales by ISO weekday (1 Monday .. 7 Sunday) and hour: when the line was put on
     * the bill, or for lines from before that was kept, when the bill was paid.
     */
    @Query(value = "SELECT CAST(EXTRACT(ISODOW FROM t) AS int) AS dow, CAST(EXTRACT(HOUR FROM t) AS int) AS hr, " +
                   "SUM(COALESCE(line_total, 0)) AS amount, SUM(COALESCE(quantity, 1)) AS units FROM (" +
                   "  SELECT COALESCE(b.sold_at, so.paid_at) AS t, b.line_total, b.quantity FROM bar_sales b " +
                   "  LEFT JOIN sales_opened so ON so.uid = b.sales_opened " +
                   "  WHERE b.branch_uid = :branchUID AND b.is_active = true AND b.created_at >= :from AND b.created_at < :to" +
                   ") x WHERE t IS NOT NULL GROUP BY 1, 2", nativeQuery = true)
    List<Object[]> salesByHour(@Param("branchUID") String branchUID, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Every pot of the branch since it began: name, collected, spent. */
    @Query("SELECT i.name, SUM(COALESCE(i.income, 0)), SUM(COALESCE(i.expenses, 0)) FROM IncomeExpenses i " +
           "WHERE i.branchUid = :branchUID AND i.isActive = true GROUP BY i.name")
    List<Object[]> potsAllTime(@Param("branchUID") String branchUID);

    /** The same for the weeks starting in [from, to). */
    @Query("SELECT i.name, SUM(COALESCE(i.income, 0)), SUM(COALESCE(i.expenses, 0)) FROM IncomeExpenses i " +
           "WHERE i.branchUid = :branchUID AND i.isActive = true AND i.weekStartDate >= :from AND i.weekStartDate < :to GROUP BY i.name")
    List<Object[]> potsInWeeks(@Param("branchUID") String branchUID, @Param("from") LocalDate from, @Param("to") LocalDate to);
}

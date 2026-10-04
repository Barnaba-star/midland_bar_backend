package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.StockTakeLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StockTakeLineRepository extends JpaRepository<StockTakeLine, String> {

    /** Differences first, largest loss at the top. */
    @Query("SELECT l FROM StockTakeLine l WHERE l.stockTake.uid = :takeUid AND l.isActive = true ORDER BY l.differenceValue ASC, l.serviceName")
    List<StockTakeLine> findByTake(@Param("takeUid") String takeUid);

    /**
     * Per product over [from, to): name, code, unit, pack, units per pack,
     * counts it was in, units missing or over, and their value.
     */
    @Query("SELECT l.barServiceUid, MAX(l.serviceName), MAX(l.serviceCode), MAX(l.unit), MAX(l.packUnit), MAX(l.unitsPerPack), " +
           "COUNT(l), SUM(l.differenceUnits), SUM(l.differenceValue) FROM StockTakeLine l " +
           "WHERE l.branchUid = :branchUID AND l.isActive = true AND l.stockTake.takenAt >= :from AND l.stockTake.takenAt < :to " +
           "GROUP BY l.barServiceUid ORDER BY SUM(l.differenceValue) ASC")
    List<Object[]> varianceByProduct(@Param("branchUID") String branchUID, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

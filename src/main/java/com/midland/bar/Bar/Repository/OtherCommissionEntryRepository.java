package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.OtherCommissionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OtherCommissionEntryRepository extends JpaRepository<OtherCommissionEntry, String> {

    /** Item name and what it got, largest first, for [start, end). */
    @Query("SELECT e.itemName, SUM(e.amount) FROM OtherCommissionEntry e " +
           "WHERE e.branchUid = :branchUID AND e.isActive = true AND e.entryDate >= :start AND e.entryDate < :end " +
           "GROUP BY e.itemName ORDER BY SUM(e.amount) DESC")
    List<Object[]> totalsByItem(@Param("branchUID") String branchUID, @Param("start") LocalDate start, @Param("end") LocalDate end);
}

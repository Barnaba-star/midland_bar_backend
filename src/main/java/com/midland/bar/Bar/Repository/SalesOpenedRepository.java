package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.SalesOpened;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SalesOpenedRepository extends JpaRepository<SalesOpened, String> {
    @Query("SELECT s FROM SalesOpened s WHERE s.branchUid = :branchUID AND s.paymentStatus='PENDING' AND s.createdAt=:date ")
    List<SalesOpened> salesOpenedList(String branchUID, LocalDate date);
    @Query("""
    SELECT s
    FROM SalesOpened s
    WHERE s.branchUid = :branchUID
      AND s.createdAt >= :startDate
      AND s.createdAt < :endDate
""")
    List<SalesOpened> salesOpenedListByStatus(
            @Param("branchUID") String branchUID,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Sales still open and unpaid. Not scoped to today on purpose - a bill
    // left from last week is exactly the one worth chasing.
    @Query("SELECT COUNT(s) FROM SalesOpened s WHERE s.branchUid = :branchUID AND s.paymentStatus = 'PENDING'")
    long countPendingBills(@Param("branchUID") String branchUID);

    @Query("""
            SELECT COALESCE(SUM(COALESCE(s.bill,0) - COALESCE(s.paidAmount,0)), 0)
            FROM SalesOpened s
            WHERE s.branchUid = :branchUID AND s.paymentStatus = 'PENDING'
            """)
    long pendingBillsAmount(@Param("branchUID") String branchUID);
}

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
    /**
     * Every unpaid bill in the branch, whatever day it was opened - an
     * unpaid bill from last night still holds its code and still owes.
     */
    @Query("SELECT s FROM SalesOpened s WHERE s.branchUid = :branchUID AND s.paymentStatus = 'PENDING' ORDER BY s.salesCode")
    List<SalesOpened> findOpenBills(@Param("branchUID") String branchUID);

    /** The bill, locked until the transaction ends - for paying it. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SalesOpened s WHERE s.uid = :uid AND s.branchUid = :branchUID")
    java.util.Optional<SalesOpened> findForUpdate(@Param("uid") String uid, @Param("branchUID") String branchUID);

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

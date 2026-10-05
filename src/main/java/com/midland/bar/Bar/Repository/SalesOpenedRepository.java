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
    /** Bills noted "paid by phone" in [from, to), newest first - cancelled ones left out. */
    @org.springframework.data.jpa.repository.Query("SELECT s FROM SalesOpened s WHERE s.branchUid = :branchUID AND s.paymentNoteMethod IS NOT NULL " +
            "AND s.paymentNoteAt >= :from AND s.paymentNoteAt < :to AND (s.paymentStatus IS NULL OR s.paymentStatus <> 'CANCELLED') ORDER BY s.paymentNoteAt DESC")
    java.util.List<SalesOpened> findPaymentNotes(@org.springframework.data.repository.query.Param("branchUID") String branchUID,
                                                 @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                                                 @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);

    /**
     * Every unpaid bill in the branch, whatever day it was opened - an
     * unpaid bill from last night still holds its code and still owes.
     */
    @Query("SELECT s FROM SalesOpened s WHERE s.branchUid = :branchUID AND s.paymentStatus = 'PENDING' ORDER BY s.salesCode")
    List<SalesOpened> findOpenBills(@Param("branchUID") String branchUID);

    /** A staff member's unpaid bills, for Staff Sell. */
    @Query("SELECT s FROM SalesOpened s WHERE s.branchUid = :branchUID AND s.staffUid = :staffUid AND s.paymentStatus = 'PENDING' ORDER BY s.salesCode")
    List<SalesOpened> findOpenBillsByStaff(@Param("staffUid") String staffUid, @Param("branchUID") String branchUID);

    /** Unpaid bills per staff member: [staffCode, staffName, bills, amount]. Null code = opened on the Sales page. */
    @Query("SELECT s.staffCode, s.staffName, COUNT(s), COALESCE(SUM(s.bill), 0) FROM SalesOpened s WHERE s.branchUid = :branchUID AND s.paymentStatus = 'PENDING' GROUP BY s.staffCode, s.staffName")
    List<Object[]> openBillsByStaff(@Param("branchUID") String branchUID);

    /** Codes held by unpaid bills in the branch - a new staff bill takes the first free K1-n. */
    @Query("SELECT s.salesCode FROM SalesOpened s WHERE s.branchUid = :branchUID AND s.paymentStatus = 'PENDING' AND s.salesCode IS NOT NULL")
    List<String> findOpenBillCodes(@Param("branchUID") String branchUID);

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
      AND (s.isActive IS NULL OR s.isActive = true)
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

package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.DebtEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DebtEntryRepository extends JpaRepository<DebtEntry, String> {

    /** What one customer owes: charges less payments and write-offs. */
    @Query("SELECT COALESCE(SUM(CASE WHEN e.kind = 'CHARGE' THEN e.amount ELSE -e.amount END), 0) " +
           "FROM DebtEntry e WHERE e.customerUid = :customerUid")
    long balanceOf(@Param("customerUid") String customerUid);

    /** Every customer's balance in a branch: [customerUid, balance]. */
    @Query("SELECT e.customerUid, COALESCE(SUM(CASE WHEN e.kind = 'CHARGE' THEN e.amount ELSE -e.amount END), 0) " +
           "FROM DebtEntry e WHERE e.branchUid = :branchUID GROUP BY e.customerUid")
    List<Object[]> balances(@Param("branchUID") String branchUID);

    /** One customer's account, newest first. */
    @Query("SELECT e FROM DebtEntry e WHERE e.customerUid = :customerUid AND e.branchUid = :branchUID ORDER BY e.entryAt DESC")
    List<DebtEntry> ledger(@Param("customerUid") String customerUid, @Param("branchUID") String branchUID);

    /** A branch's charges, newest first - read to find how long each balance has been owed. */
    @Query("SELECT e FROM DebtEntry e WHERE e.branchUid = :branchUID AND e.kind = 'CHARGE' ORDER BY e.entryAt DESC")
    List<DebtEntry> chargesNewestFirst(@Param("branchUID") String branchUID);

    /** A branch's lines in [from, to), newest first. */
    @Query("SELECT e FROM DebtEntry e WHERE e.branchUid = :branchUID AND e.entryAt >= :from AND e.entryAt < :to ORDER BY e.entryAt DESC")
    List<DebtEntry> between(@Param("branchUID") String branchUID, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Debt payments one login took in [from, to), per method: [method, amount, count] - they belong in that login's cash-up. */
    @Query("SELECT e.method, COALESCE(SUM(e.amount), 0), COUNT(e) FROM DebtEntry e " +
           "WHERE e.branchUid = :branchUID AND e.entryBy = :email AND e.kind = 'PAYMENT' " +
           "AND e.entryAt >= :from AND e.entryAt < :to GROUP BY e.method")
    List<Object[]> paymentsTakenBy(@Param("branchUID") String branchUID, @Param("email") String email,
                                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Debt payments one login took in [from, to), oldest first - listed on their cash-up. */
    @Query("SELECT e FROM DebtEntry e WHERE e.branchUid = :branchUID AND e.entryBy = :email AND e.kind = 'PAYMENT' " +
           "AND e.entryAt >= :from AND e.entryAt < :to ORDER BY e.entryAt")
    List<DebtEntry> paymentRowsTakenBy(@Param("branchUID") String branchUID, @Param("email") String email,
                                       @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

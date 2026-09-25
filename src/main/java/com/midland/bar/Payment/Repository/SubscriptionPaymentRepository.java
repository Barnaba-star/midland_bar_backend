package com.midland.bar.Payment.Repository;

import com.midland.bar.Payment.Model.SubscriptionPayment;
import com.midland.bar.Payment.Projection.PaymentTotalsProjection;
import com.midland.bar.Payment.Projection.StaffEarningProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, String> {

    // Snippe retries any webhook that doesn't answer 2xx fast enough, so the
    // same payment.completed event can arrive more than once.
    boolean existsByReference(String reference);

    /**
     * What each staff member earned between two dates. Staff who registered
     * branches but had none pay in the window simply don't appear here - the
     * service fills them in with zeros from the branch counts.
     */
    @Query("""
    SELECT
        p.staffUid AS staffUid,
        MIN(p.staffName) AS staffName,
        COUNT(DISTINCT p.branchUid) AS branchesPaid,
        COALESCE(SUM(p.amount), 0) AS totalCollected,
        COALESCE(SUM(p.commissionAmount), 0) AS commissionDue,
        COUNT(p) AS payments
    FROM SubscriptionPayment p
    WHERE p.staffUid IS NOT NULL
      AND p.paidAt >= :start
      AND p.paidAt <= :end
    GROUP BY p.staffUid
""")
    List<StaffEarningProjection> earningsBetween(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * The individual payments behind one staff member's total, so the report
     * can be drilled into and checked branch by branch before paying out.
     */
    @Query("""
    SELECT p
    FROM SubscriptionPayment p
    WHERE p.staffUid = :staffUid
      AND p.paidAt >= :start
      AND p.paidAt <= :end
    ORDER BY p.paidAt DESC
""")
    List<SubscriptionPayment> findStaffPayments(
            @Param("staffUid") String staffUid,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    // Totals across everything, not just the page on screen. "How much has
    // come in" is the question this page exists to answer, and a page total
    // answers a different one.
    @Query("""
            SELECT COUNT(p) AS payments,
                   COALESCE(SUM(p.amount), 0) AS totalAmount,
                   COALESCE(SUM(p.commissionAmount), 0) AS totalCommission
            FROM SubscriptionPayment p
            """)
    PaymentTotalsProjection totals();

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM SubscriptionPayment p
            WHERE p.paidAt >= :start
            """)
    long amountSince(@Param("start") java.time.LocalDate start);

    // A month's takings and the staff commission already carved out of them.
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0) AS totalAmount,
                   COALESCE(SUM(p.commissionAmount), 0) AS totalCommission,
                   COUNT(p) AS payments
            FROM SubscriptionPayment p
            WHERE p.paidAt >= :start AND p.paidAt <= :end
            """)
    PaymentTotalsProjection totalsBetween(@Param("start") java.time.LocalDate start,
                                          @Param("end") java.time.LocalDate end);
}

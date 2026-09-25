package com.midland.bar.Payment.Repository;

import com.midland.bar.Payment.Model.CommissionPayout;
import com.midland.bar.Payment.Projection.PayoutTotalProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommissionPayoutRepository extends JpaRepository<CommissionPayout, String> {

    /**
     * What has already been handed over per staff member for one month, so
     * the report can show the outstanding balance instead of a flag that
     * goes stale the moment another branch pays into the same month.
     */
    @Query("""
    SELECT
        p.staffUid AS staffUid,
        COALESCE(SUM(p.amount), 0) AS amountPaid,
        MAX(p.paidAt) AS lastPaidAt
    FROM CommissionPayout p
    WHERE p.periodYear = :year
      AND p.periodMonth = :month
      AND (p.roleCode IS NULL OR p.roleCode = 'STAFF')
    GROUP BY p.staffUid
""")
    List<PayoutTotalProjection> totalsForPeriod(
            @Param("year") int year,
            @Param("month") int month
    );

    @Query("""
    SELECT p
    FROM CommissionPayout p
    WHERE p.staffUid = :staffUid
      AND p.periodYear = :year
      AND p.periodMonth = :month
    ORDER BY p.paidAt DESC
""")
    List<CommissionPayout> findForStaffPeriod(
            @Param("staffUid") String staffUid,
            @Param("year") int year,
            @Param("month") int month
    );

    /**
     * What one person has already been paid for one month against one share.
     * Null roleCode counts as STAFF - see CommissionPayout.roleCode.
     */
    @Query("""
    SELECT COALESCE(SUM(p.amount), 0)
    FROM CommissionPayout p
    WHERE p.staffUid = :uid
      AND p.periodYear = :year
      AND p.periodMonth = :month
      AND (COALESCE(p.roleCode, 'STAFF') = :roleCode)
""")
    long paidForShare(@Param("uid") String uid,
                      @Param("roleCode") String roleCode,
                      @Param("year") int year,
                      @Param("month") int month);
}

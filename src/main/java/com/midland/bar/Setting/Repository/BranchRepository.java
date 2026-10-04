package com.midland.bar.Setting.Repository;

import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Payment.Projection.BranchCountProjection;
import com.midland.bar.Setting.Projection.BranchProjection;
import com.midland.bar.Uaa.Model.Permission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, String> {
    @Query("SELECT b.uid AS uid, b.branchCode AS branchCode, b.branchCategory AS branchCategory, b.branchName as name FROM Branch b WHERE b.isActive=true")
    List<BranchProjection> findBranchList();

    @Query("SELECT b.uid AS uid, b.branchCode AS branchCode, b.branchCategory AS branchCategory, b.branchName as name FROM Branch b WHERE b.isActive=true AND b.createdBy = :createdBy")
    List<BranchProjection> findBranchListByCreator(@Param("createdBy") String createdBy);

    Page<Branch> findByCreatedBy(String createdBy, Pageable pageable);

    /**
     * The branch list with an optional search box behind it. Both parameters
     * are optional: a null search returns everything, and a null createdBy
     * lifts the "only my own branches" narrowing that STAFF gets.
     * The caller lower-cases the term - doing it here would run per row.
     */
    @Query("""
            SELECT b FROM Branch b
            WHERE (:createdBy IS NULL OR b.createdBy = :createdBy)
              AND (:search IS NULL
                   OR LOWER(b.branchName) LIKE %:search%
                   OR LOWER(b.branchCode) LIKE %:search%
                   OR LOWER(b.region) LIKE %:search%
                   OR LOWER(b.phone) LIKE %:search%)
            """)
    Page<Branch> searchBranchPage(
            @Param("search") String search,
            @Param("createdBy") String createdBy,
            Pageable pageable
    );

    Branch findByBranchCode(String branchName);

    @Query(value = """
    SELECT COALESCE(
        MAX(
            CAST(
                SUBSTRING(branch_code FROM '[0-9]+$')
                AS INTEGER
            )
        ),
        0
    )
    FROM branches
    WHERE UPPER(region) = UPPER(:region)
    """, nativeQuery = true)
    Integer findLastBranchSequenceByRegion(
            @Param("region") String region
    );

    // Flips branches whose paid/free period has lapsed to EXPIRED, skipping
    // ones already marked that way so the daily job stays cheap. Login is
    // still gated on closeSubscription directly (see UserController) so a
    // branch is blocked the moment it lapses, not only after this job next
    // runs - this is purely so the frontend badge/status stays in sync.
    @Modifying
    @Query("UPDATE Branch b SET b.subscriptionStatus = 'EXPIRED' " +
            "WHERE b.closeSubscription IS NOT NULL " +
            "AND b.closeSubscription < :today " +
            "AND b.subscriptionStatus <> 'EXPIRED'")
    int markExpiredSubscriptions(@Param("today") LocalDate today);

    // How many branches each staff member has registered, for the commission
    // report. Counts every branch they brought in, whether or not it has ever
    // paid - the difference between this and the paid count is exactly what
    // tells you which of their branches went quiet.
    @Query("""
    SELECT b.createdBy AS staffUid, COUNT(b) AS branchesRegistered
    FROM Branch b
    WHERE b.createdBy IS NOT NULL
    GROUP BY b.createdBy
""")
    List<BranchCountProjection> countBranchesByCreator();

    // Every branch one staff member registered, newest first. The commission
    // report drills into this to show which of them paid in a given month and,
    // just as usefully, which did not.
    @Query("""
    SELECT b FROM Branch b
    WHERE b.createdBy = :createdBy
    ORDER BY b.createdAt DESC
""")
    List<Branch> findAllByCreator(@Param("createdBy") String createdBy);

    /**
     * Payments that were started and never resolved - still pending, or
     * failed - and that still carry a reference to ask Snippe about.
     */
    @Query("""
            SELECT b FROM Branch b
            WHERE b.pendingPaymentRef IS NOT NULL
              AND (b.subscriptionStatus = 'PENDING' OR b.subscriptionStatus = 'FAILED')
            ORDER BY b.pendingPaymentAt DESC
            """)
    List<Branch> findUnresolvedPayments();

    /** Payments still awaiting Snippe's answer, for the scheduled reconcile. */
    @Query("""
            SELECT b FROM Branch b
            WHERE b.pendingPaymentRef IS NOT NULL
              AND b.subscriptionStatus = 'PENDING'
              AND b.pendingPaymentAt >= :since
            """)
    List<Branch> findPendingPaymentsSince(@Param("since") LocalDateTime since);

    /**
     * Branches at or past the end of their subscription, soonest first.
     *
     * The platform's own ROOT branch is left out - it never pays, and the
     * login gate exempts it, so listing it as overdue would be noise that
     * never clears.
     */
    @Query("""
            SELECT b FROM Branch b
            WHERE b.closeSubscription IS NOT NULL
              AND b.closeSubscription <= :horizon
              AND UPPER(b.branchCode) <> 'ROOT'
              AND (:createdBy IS NULL OR b.createdBy = :createdBy)
            ORDER BY b.closeSubscription ASC
            """)
    List<Branch> findExpiringBranches(@Param("horizon") LocalDate horizon,
                                      @Param("createdBy") String createdBy);

    @Query("SELECT COUNT(b) FROM Branch b WHERE UPPER(b.region) = UPPER(:region)")
    long countByRegion(@Param("region") String region);

    /** The branches a STAFF login registered - they may log in to these. */
    List<Branch> findAllByCreatedBy(String createdBy);
}

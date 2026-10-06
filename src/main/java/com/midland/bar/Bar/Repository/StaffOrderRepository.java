package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.StaffOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffOrderRepository extends JpaRepository<StaffOrder, String> {

    /** The bill's draft for one station - a bill writes drinks and food onto separate orders. Older drafts with no station are the counter's. */
    @Query("SELECT o FROM StaffOrder o WHERE o.salesOpenedUid = :billUid AND o.branchUid = :branchUID AND o.status = 'DRAFT' AND COALESCE(o.station, 'COUNTER') = :station")
    Optional<StaffOrder> findDraft(@Param("billUid") String billUid, @Param("branchUID") String branchUID, @Param("station") String station);

    @Query("SELECT o FROM StaffOrder o WHERE o.uid = :uid AND o.branchUid = :branchUID")
    Optional<StaffOrder> findByUid(@Param("uid") String uid, @Param("branchUID") String branchUID);

    /** Locked until the transaction ends, so two supervisors cannot both receive it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM StaffOrder o WHERE o.uid = :uid AND o.branchUid = :branchUID")
    Optional<StaffOrder> findForUpdate(@Param("uid") String uid, @Param("branchUID") String branchUID);

    @Query("SELECT o FROM StaffOrder o WHERE o.staffUid = :staffUid AND o.branchUid = :branchUID AND o.status = 'DRAFT'")
    List<StaffOrder> findDraftsByStaff(@Param("staffUid") String staffUid, @Param("branchUID") String branchUID);

    /** What the supervisor still has to decide, oldest first. */
    @Query("SELECT DISTINCT o FROM StaffOrder o WHERE o.branchUid = :branchUID AND o.status = 'SENT' ORDER BY o.sentAt")
    List<StaffOrder> findPending(@Param("branchUID") String branchUID);

    /** For Staff Sell: orders not yet on the bill, and ones turned back recently, on the given bills. */
    @Query("""
            SELECT DISTINCT o FROM StaffOrder o
            WHERE o.branchUid = :branchUID AND o.salesOpenedUid IN :billUids
              AND (o.status IN ('DRAFT', 'SENT') OR (o.status = 'REJECTED' AND o.decidedAt >= :since))
            ORDER BY o.salesOpenedUid
            """)
    List<StaffOrder> findShownOnBills(@Param("billUids") Collection<String> billUids,
                                      @Param("branchUID") String branchUID,
                                      @Param("since") LocalDateTime since);

    /** Orders still to be decided on a bill - it cannot be paid while there are any. */
    @Query("SELECT COUNT(o) FROM StaffOrder o WHERE o.salesOpenedUid = :billUid AND o.status IN ('DRAFT', 'SENT') AND SIZE(o.lines) > 0")
    long countUndecided(@Param("billUid") String billUid);

    /** Orders that went onto bills offline and the supervisor has not looked over yet. */
    @Query("SELECT DISTINCT o FROM StaffOrder o WHERE o.branchUid = :branchUID AND o.offline = true AND o.reviewedAt IS NULL ORDER BY o.decidedAt")
    List<StaffOrder> findOfflineUnreviewed(@Param("branchUID") String branchUID);
}

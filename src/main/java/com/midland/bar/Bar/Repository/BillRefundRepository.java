package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BillRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRefundRepository extends JpaRepository<BillRefund, String> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BillRefund r WHERE r.uid = :uid AND r.branchUid = :branchUID")
    Optional<BillRefund> findForUpdate(@Param("uid") String uid, @Param("branchUID") String branchUID);

    @Query("SELECT r FROM BillRefund r WHERE r.branchUid = :branchUID AND r.status = 'PENDING' ORDER BY r.requestedAt")
    List<BillRefund> pending(@Param("branchUID") String branchUID);

    /** Asked for, or decided, in [from, to) - newest first. */
    @Query("SELECT r FROM BillRefund r WHERE r.branchUid = :branchUID AND " +
           "((r.requestedAt >= :from AND r.requestedAt < :to) OR (r.decidedAt >= :from AND r.decidedAt < :to)) ORDER BY r.requestedAt DESC")
    List<BillRefund> between(@Param("branchUID") String branchUID, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT r FROM BillRefund r WHERE r.billUid = :billUid AND r.status <> 'REJECTED'")
    List<BillRefund> forBill(@Param("billUid") String billUid);

    /** Money one login handed back in [from, to) - off their cash-up. Deni moved no money. */
    @Query("SELECT r FROM BillRefund r WHERE r.branchUid = :branchUID AND r.requestedBy = :email AND r.status = 'APPROVED' " +
           "AND r.method <> 'deni' AND r.decidedAt >= :from AND r.decidedAt < :to ORDER BY r.decidedAt")
    List<BillRefund> paidOutBy(@Param("branchUID") String branchUID, @Param("email") String email,
                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

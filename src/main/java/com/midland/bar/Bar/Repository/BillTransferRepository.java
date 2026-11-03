package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BillTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BillTransferRepository extends JpaRepository<BillTransfer, String> {

    /** Bills moved between staff in a period, newest first. */
    @Query("SELECT t FROM BillTransfer t WHERE t.branchUid = :branchUID AND t.isActive = true AND t.transferredAt >= :from AND t.transferredAt < :to ORDER BY t.transferredAt DESC")
    List<BillTransfer> findBetween(@Param("branchUID") String branchUID, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

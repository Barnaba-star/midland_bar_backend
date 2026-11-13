package com.midland.bar.Bar.Table;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback, String> {

    @Query("SELECT f FROM CustomerFeedback f WHERE f.branchUid = :branchUID AND f.isActive = true AND f.sentAt >= :from AND f.sentAt < :to ORDER BY f.sentAt DESC")
    List<CustomerFeedback> findIn(@Param("branchUID") String branchUID, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(f) FROM CustomerFeedback f WHERE f.branchUid = :branchUID AND f.isActive = true AND f.readAt IS NULL")
    long countUnread(@Param("branchUID") String branchUID);

    @Query("SELECT f FROM CustomerFeedback f WHERE f.uid = :uid AND f.branchUid = :branchUID AND f.isActive = true")
    Optional<CustomerFeedback> findInBranch(@Param("uid") String uid, @Param("branchUID") String branchUID);

    /** Every message already read in a branch - "Futa yaliyosomwa" (owner, 2026-11-13). */
    @Modifying
    @Query("DELETE FROM CustomerFeedback f WHERE f.branchUid = :branchUID AND f.readAt IS NOT NULL")
    int deleteRead(@Param("branchUID") String branchUID);

    /** Sent from one table since a time - the anti-spam limit. */
    @Query("SELECT COUNT(f) FROM CustomerFeedback f WHERE f.tableUid = :tableUid AND f.sentAt >= :since")
    long countFromTableSince(@Param("tableUid") String tableUid, @Param("since") LocalDateTime since);
}

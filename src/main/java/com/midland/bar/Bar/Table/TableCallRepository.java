package com.midland.bar.Bar.Table;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TableCallRepository extends JpaRepository<TableCall, String> {

    /** Open calls of a branch, oldest first, from the last few hours only. */
    @Query("SELECT c FROM TableCall c WHERE c.branchUid = :branchUID AND c.isActive = true AND c.doneAt IS NULL AND c.calledAt >= :since ORDER BY c.calledAt")
    List<TableCall> findOpen(@Param("branchUID") String branchUID, @Param("since") LocalDateTime since);

    @Query("SELECT c FROM TableCall c WHERE c.tableUid = :tableUid AND c.kind = :kind AND c.isActive = true AND c.doneAt IS NULL AND c.calledAt >= :since")
    Optional<TableCall> findOpenFor(@Param("tableUid") String tableUid, @Param("kind") String kind, @Param("since") LocalDateTime since);

    @Query("SELECT c FROM TableCall c WHERE c.uid = :uid AND c.branchUid = :branchUID AND c.isActive = true")
    Optional<TableCall> findInBranch(@Param("uid") String uid, @Param("branchUID") String branchUID);
}

package com.midland.bar.Support.Repository;

import com.midland.bar.Support.Model.BranchMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BranchMessageRepository extends JpaRepository<BranchMessage, String> {

    /**
     * The admin list. Both filters are optional, so one query serves every
     * combination of "all of them", "only the open ones" and "only this
     * branch's".
     */
    @Query("""
            SELECT m FROM BranchMessage m
            WHERE (:status IS NULL OR m.status = :status)
              AND (:branchUid IS NULL OR m.branch.uid = :branchUid)
              AND (:search IS NULL
                   OR LOWER(m.subject) LIKE %:search%
                   OR LOWER(m.body) LIKE %:search%
                   OR LOWER(m.branchName) LIKE %:search%)
            ORDER BY m.lastActivityAt DESC
            """)
    Page<BranchMessage> findMessages(@Param("status") String status,
                                     @Param("branchUid") String branchUid,
                                     @Param("search") String search,
                                     Pageable pageable);

    /** One branch's own threads, newest activity first. */
    @Query("""
            SELECT m FROM BranchMessage m
            WHERE m.branch.uid = :branchUid
            ORDER BY m.lastActivityAt DESC
            """)
    List<BranchMessage> findForBranch(@Param("branchUid") String branchUid);

    /** For the badge: how many are still waiting on an answer. */
    @Query("SELECT COUNT(m) FROM BranchMessage m WHERE m.status <> 'CLOSED' AND m.awaitingReply = true")
    long countAwaitingReply();
}

package com.midland.bar.Bar.Table;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TableOrderRepository extends JpaRepository<TableOrder, String> {
    @Query("SELECT o FROM TableOrder o WHERE o.branchUid = :branchUID AND o.isActive = true AND o.status = 'PENDING' AND o.placedAt >= :since ORDER BY o.placedAt")
    List<TableOrder> findPending(@Param("branchUID") String branchUID, @Param("since") LocalDateTime since);

    @Query("SELECT o FROM TableOrder o WHERE o.tableUid = :tableUid AND o.isActive = true AND o.placedAt >= :since ORDER BY o.placedAt DESC")
    List<TableOrder> findForTable(@Param("tableUid") String tableUid, @Param("since") LocalDateTime since);

    @Query("SELECT o FROM TableOrder o WHERE o.uid = :uid AND o.branchUid = :branchUID AND o.isActive = true")
    Optional<TableOrder> findInBranch(@Param("uid") String uid, @Param("branchUID") String branchUID);

    @Query("SELECT COUNT(o) FROM TableOrder o WHERE o.tableUid = :tableUid AND o.placedAt >= :since")
    long countFromTableSince(@Param("tableUid") String tableUid, @Param("since") LocalDateTime since);
}

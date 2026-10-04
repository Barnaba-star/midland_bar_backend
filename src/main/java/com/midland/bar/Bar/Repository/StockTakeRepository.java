package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.StockTake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockTakeRepository extends JpaRepository<StockTake, String> {

    @Query("SELECT t FROM StockTake t WHERE t.branchUid = :branchUID AND t.isActive = true " +
           "AND t.takenAt >= :from AND t.takenAt < :to ORDER BY t.takenAt DESC")
    List<StockTake> findTaken(@Param("branchUID") String branchUID, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT t FROM StockTake t WHERE t.uid = :uid AND t.branchUid = :branchUID")
    Optional<StockTake> findInBranch(@Param("uid") String uid, @Param("branchUID") String branchUID);
}

package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.StaffLoss;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StaffLossRepository extends JpaRepository<StaffLoss, String> {

    /** Shortages one login recorded in [from, to) - they come off that cashier's expected cash. */
    @Query("SELECT l FROM StaffLoss l WHERE l.branchUid = :branchUID AND l.recordedBy = :email AND l.isActive = true " +
           "AND l.recordedAt >= :from AND l.recordedAt < :to ORDER BY l.recordedAt")
    List<StaffLoss> recordedBy(@Param("branchUID") String branchUID, @Param("email") String email,
                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

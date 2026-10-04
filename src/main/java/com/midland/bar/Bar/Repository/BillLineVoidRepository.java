package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BillLineVoid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BillLineVoidRepository extends JpaRepository<BillLineVoid, String> {

    /** Taken off bills in [from, to), newest first; email null means everyone's. */
    @Query("SELECT v FROM BillLineVoid v WHERE v.branchUid = :branchUID AND v.isActive = true " +
           "AND v.voidedAt >= :from AND v.voidedAt < :to AND (:email IS NULL OR v.voidedBy = :email) ORDER BY v.voidedAt DESC")
    List<BillLineVoid> findIn(@Param("branchUID") String branchUID, @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to, @Param("email") String email);
}

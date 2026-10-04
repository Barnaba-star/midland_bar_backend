package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.CashUp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CashUpRepository extends JpaRepository<CashUp, String> {

    @Query("SELECT MAX(c.periodTo) FROM CashUp c WHERE c.branchUid = :branchUID AND c.cashierEmail = :email AND c.isActive = true")
    Optional<LocalDateTime> lastClosedAt(@Param("branchUID") String branchUID, @Param("email") String email);

    /** Cash-ups closed in [from, to), newest first; email null means every cashier. */
    @Query("SELECT c FROM CashUp c WHERE c.branchUid = :branchUID AND c.isActive = true " +
           "AND c.periodTo >= :from AND c.periodTo < :to AND (:email IS NULL OR c.cashierEmail = :email) ORDER BY c.periodTo DESC")
    List<CashUp> findClosed(@Param("branchUID") String branchUID, @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to, @Param("email") String email);

    @Query("SELECT c FROM CashUp c WHERE c.uid = :uid AND c.branchUid = :branchUID")
    Optional<CashUp> findInBranch(@Param("uid") String uid, @Param("branchUID") String branchUID);
}

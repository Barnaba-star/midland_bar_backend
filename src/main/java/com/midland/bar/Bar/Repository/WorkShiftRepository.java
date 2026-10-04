package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.WorkShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkShiftRepository extends JpaRepository<WorkShift, String> {

    /** A login's shift that is OPEN or CLOSED (awaiting cash-up) - there is at most one. */
    @Query("SELECT s FROM WorkShift s WHERE s.branchUid = :branchUID AND s.cashierEmail = :email " +
           "AND s.isActive = true AND s.status IN ('OPEN', 'CLOSED') ORDER BY s.openedAt DESC")
    List<WorkShift> findUnfinished(@Param("branchUID") String branchUID, @Param("email") String email);

    /** Shifts opened in [from, to), newest first; email null means every seller. */
    @Query("SELECT s FROM WorkShift s WHERE s.branchUid = :branchUID AND s.isActive = true " +
           "AND s.openedAt >= :from AND s.openedAt < :to AND (:email IS NULL OR s.cashierEmail = :email) ORDER BY s.openedAt DESC")
    List<WorkShift> findOpenedIn(@Param("branchUID") String branchUID, @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to, @Param("email") String email);

    /** Still open now, whenever they were opened - a shift running for days must not drop off the list. */
    @Query("SELECT s FROM WorkShift s WHERE s.branchUid = :branchUID AND s.isActive = true " +
           "AND s.status IN ('OPEN', 'CLOSED') AND (:email IS NULL OR s.cashierEmail = :email) ORDER BY s.openedAt DESC")
    List<WorkShift> findAllUnfinished(@Param("branchUID") String branchUID, @Param("email") String email);

    /** When this login's last finished shift ended. */
    @Query("SELECT MAX(s.closedAt) FROM WorkShift s WHERE s.branchUid = :branchUID AND s.cashierEmail = :email AND s.isActive = true AND s.closedAt IS NOT NULL")
    Optional<LocalDateTime> lastClosedAt(@Param("branchUID") String branchUID, @Param("email") String email);
}

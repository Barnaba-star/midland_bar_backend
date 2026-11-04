package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.LoyaltyVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoyaltyVisitRepository extends JpaRepository<LoyaltyVisit, String> {

    /** Visits still counting (no reward yet) since a time, oldest first. */
    @Query("SELECT v FROM LoyaltyVisit v WHERE v.isActive = true AND v.customerUid = :c AND v.rewardUid IS NULL AND v.visitAt >= :since ORDER BY v.visitAt")
    List<LoyaltyVisit> counting(@Param("c") String customerUid, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(v) FROM LoyaltyVisit v WHERE v.isActive = true AND v.billUid = :bill")
    long countForBill(@Param("bill") String billUid);

    /** [customerUid, visits still counting] for a branch since a time. */
    @Query("SELECT v.customerUid, COUNT(v) FROM LoyaltyVisit v WHERE v.isActive = true AND v.branchUid = :b AND v.rewardUid IS NULL AND v.visitAt >= :since GROUP BY v.customerUid")
    List<Object[]> countingByCustomer(@Param("b") String branchUID, @Param("since") LocalDateTime since);
}

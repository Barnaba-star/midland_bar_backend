package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.OfferUse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OfferUseRepository extends JpaRepository<OfferUse, String> {

    /** What one customer was given free since a time (the month's start). */
    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM OfferUse o WHERE o.isActive = true AND o.customerUid = :c AND o.usedAt >= :since")
    long usedSince(@Param("c") String customerUid, @Param("since") LocalDateTime since);

    /** [customerUid, used] this month for a branch. */
    @Query("SELECT o.customerUid, COALESCE(SUM(o.amount), 0) FROM OfferUse o WHERE o.isActive = true AND o.branchUid = :b AND o.usedAt >= :since GROUP BY o.customerUid")
    List<Object[]> usedByCustomerSince(@Param("b") String branchUID, @Param("since") LocalDateTime since);

    @Query("SELECT o FROM OfferUse o WHERE o.isActive = true AND o.branchUid = :b AND o.usedAt >= :from AND o.usedAt < :to ORDER BY o.usedAt DESC")
    List<OfferUse> between(@Param("b") String branchUID, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT o FROM OfferUse o WHERE o.isActive = true AND o.customerUid = :c ORDER BY o.usedAt DESC")
    List<OfferUse> ofCustomer(@Param("c") String customerUid);

    @Query("SELECT o FROM OfferUse o WHERE o.isActive = true AND o.billUid = :bill")
    List<OfferUse> ofBill(@Param("bill") String billUid);
}

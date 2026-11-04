package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.LoyaltyReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoyaltyRewardRepository extends JpaRepository<LoyaltyReward, String> {

    /** Rewards not yet expired, soonest to expire first - spent in that order. */
    @Query("SELECT r FROM LoyaltyReward r WHERE r.isActive = true AND r.customerUid = :c AND r.expiresAt > :now ORDER BY r.expiresAt")
    List<LoyaltyReward> live(@Param("c") String customerUid, @Param("now") LocalDateTime now);

    @Query("SELECT r FROM LoyaltyReward r WHERE r.isActive = true AND r.branchUid = :b AND r.expiresAt > :now")
    List<LoyaltyReward> liveInBranch(@Param("b") String branchUID, @Param("now") LocalDateTime now);
}

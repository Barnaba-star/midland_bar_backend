package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.LoyaltyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoyaltyRuleRepository extends JpaRepository<LoyaltyRule, String> {

    @Query("SELECT r FROM LoyaltyRule r WHERE r.isActive = true AND r.branchUid = :b")
    Optional<LoyaltyRule> findInBranch(@Param("b") String branchUID);
}

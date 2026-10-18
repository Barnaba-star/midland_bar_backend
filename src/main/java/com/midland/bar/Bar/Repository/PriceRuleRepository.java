package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.PriceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRuleRepository extends JpaRepository<PriceRule, String> {

    @Query("SELECT r FROM PriceRule r WHERE r.branchUid = :branchUID ORDER BY r.startTime, r.name")
    List<PriceRule> findInBranch(@Param("branchUID") String branchUID);

    @Query("SELECT r FROM PriceRule r WHERE r.branchUid = :branchUID AND r.enabled = true")
    List<PriceRule> enabledInBranch(@Param("branchUID") String branchUID);

    @Query("SELECT r FROM PriceRule r WHERE r.uid = :uid AND r.branchUid = :branchUID")
    Optional<PriceRule> findInBranch(@Param("uid") String uid, @Param("branchUID") String branchUID);
}

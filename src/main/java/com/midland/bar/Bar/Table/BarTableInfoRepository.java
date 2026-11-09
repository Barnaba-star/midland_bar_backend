package com.midland.bar.Bar.Table;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BarTableInfoRepository extends JpaRepository<BarTableInfo, String> {
    @Query("SELECT i FROM BarTableInfo i WHERE i.branchUid = :branchUID AND i.isActive = true")
    Optional<BarTableInfo> findInBranch(@Param("branchUID") String branchUID);
}

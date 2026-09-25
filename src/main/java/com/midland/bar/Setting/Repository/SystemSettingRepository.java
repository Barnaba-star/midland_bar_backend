package com.midland.bar.Setting.Repository;

import com.midland.bar.Setting.Model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    // Indexed on branch_uid (see SystemSetting) so this stays a
    // single-row lookup no matter how many branches (~1000+) exist.
    @Query("SELECT s FROM SystemSetting s WHERE s.branchUid = :branchUid")
    Optional<SystemSetting> findByBranchUid(@Param("branchUid") String branchUid);
}

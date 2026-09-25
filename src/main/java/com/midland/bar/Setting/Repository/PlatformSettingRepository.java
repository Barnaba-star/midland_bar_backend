package com.midland.bar.Setting.Repository;

import com.midland.bar.Setting.Model.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {

    /** There is only ever one row; this is how it is fetched without knowing its uid. */
    Optional<PlatformSetting> findFirstByIsActiveTrue();
}

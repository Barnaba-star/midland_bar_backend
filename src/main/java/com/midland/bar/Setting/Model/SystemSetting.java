package com.midland.bar.Setting.Model;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

/**
 * Per-branch settings — one row per branch, looked up by branchUid.
 * A branch with no row yet (or no logoImage set) has no custom logo;
 * the frontend falls back to the default asset in that case.
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "system_settings", indexes = {
        @Index(name = "idx_system_setting_branch", columnList = "branch_uid", unique = true)
})
public class SystemSetting extends BaseEntity {

    @Column(name = "branch_uid", nullable = false)
    private String branchUid;

    @Column(name = "logo_image")
    private String logoImage;

    @Column(name = "company_name")
    private String companyName;
}

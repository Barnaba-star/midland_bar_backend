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

    /* TRA (owner, 2026-10-18): printed on every receipt; the EFD/VFD link comes later. */
    @Column(name = "tax_tin", length = 20)
    private String taxTin;

    @Column(name = "tax_vrn", length = 20)
    private String taxVrn;

    @Column(name = "efd_serial", length = 40)
    private String efdSerial;

    /** The most a cashier may take off a bill on their own, in percent; above it a CEO or manager does. */
    @Column(name = "cashier_discount_percent")
    private Integer cashierDiscountPercent;

    /* Theft alerts to the CEO: items taken off bills in one shift, a cash-up short by, a shift open for. */
    @Column(name = "alert_voids_per_shift")
    private Integer alertVoidsPerShift;

    @Column(name = "alert_shortage_amount")
    private Integer alertShortageAmount;

    @Column(name = "alert_shift_hours")
    private Integer alertShiftHours;

    public int cashierDiscountPercentOrDefault() { return cashierDiscountPercent == null ? 10 : cashierDiscountPercent; }
    public int alertVoidsOrDefault() { return alertVoidsPerShift == null ? 5 : alertVoidsPerShift; }
    public int alertShortageOrDefault() { return alertShortageAmount == null ? 10_000 : alertShortageAmount; }
    public int alertShiftHoursOrDefault() { return alertShiftHours == null ? 14 : alertShiftHours; }
}

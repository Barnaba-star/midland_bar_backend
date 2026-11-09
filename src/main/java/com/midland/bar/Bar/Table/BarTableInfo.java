package com.midland.bar.Bar.Table;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What a branch shows customers in the tables' window besides the menu
 * (owner, 2026-11-09): WiFi, opening hours, phone, events, and the bar's own
 * payment numbers (Lipa namba) - customers pay the bar directly, then tap
 * "Nimelipa" so the cashier can check. One row per branch.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "bar_table_info", indexes = @Index(name = "idx_bar_table_info_branch", columnList = "branch_uid"))
public class BarTableInfo extends TenantEntity {
    @Column(name = "wifi_name", length = 60) private String wifiName;
    @Column(name = "wifi_password", length = 60) private String wifiPassword;
    @Column(name = "hours", length = 120) private String hours;
    @Column(name = "phone", length = 30) private String phone;
    @Column(name = "note", length = 500) private String note;
    @Column(name = "pay_mpesa", length = 60) private String payMpesa;
    @Column(name = "pay_tigo", length = 60) private String payTigo;
    @Column(name = "pay_airtel", length = 60) private String payAirtel;
    @Column(name = "pay_halo", length = 60) private String payHalo;
    @Column(name = "pay_bank", length = 120) private String payBank;
}

package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "commissions",
indexes = {
        @Index(name = "idx_commission_branch", columnList = "branch_uid"),
        @Index(name = "idx_commission_service", columnList = "bar_service"),
        @Index(name = "idx_commission_active", columnList = "is_active")
})
public class   Commission extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_service")
    @JsonIgnore
    private BarServiceEntity barService;

    @Column(name = "staff_percent")
    private Integer staffPercent;

    @Column(name = "owner_percent")
    private Integer ownerPercent;

    @Column(name = "tra_percent")
    private Integer  traPercent;

    @Column(name = "emergency_percent")
    private Integer emergencyPercent;

    @Column(name = "total_percent")
    private Integer totalPercent;

    @Column(name = "maintenance_percent")
    private Integer maintenancePercent;

    @Column(name = "other_percent")
    private Integer otherPercent;

    @Column(name = "luku_percent")
    private Integer lukuPercent;

    @Column(name = "water_percent")
    private Integer waterPercent;

    @Column(name = "rent_percent")
    private Integer rentPercent;

    @Column(name = "loan_percent")
    private Integer loanPercent;

    @Column(name = "stock_purchase_percent")
    private Integer stockPurchasePercent;
}

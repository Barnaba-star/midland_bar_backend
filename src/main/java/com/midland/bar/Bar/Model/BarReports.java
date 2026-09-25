package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
@Table(name = "bar_reports", indexes = {
        @Index(
                name = "idx_bar_reports_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_bar_reports_active",
                columnList = "is_active"
        )
})
public class BarReports extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_staff_uid")
    private BarStaff barStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_service_uid")
    private BarServiceEntity barServiceEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_sales")
    private BarSales barSales;

    @Column(name = "tra_amount")
    private Integer traAmount;

    @Column(name = "emergency_amount")
    private Integer emergencyAmount;

    @Column(name = "other_amount")
    private Integer othersAmount;

    @Column(name = "owner_amount")
    private Integer ownerAmount;

    @Column(name = "staff_amount")
    private Integer staffAmount;

    @Column(name = "maintenance_amount")
    private Integer maintenanceAmount;

    @Column(name = "luku_percent")
    private Integer lukuAmount;

    @Column(name = "water_percent")
    private Integer waterAmount;

    @Column(name = "rent_percent")
    private Integer rentAmount;

    @Column(name = "loan_percent")
    private Integer loanAmount;

    @Column(name = "stock_purchase_percent")
    private Integer stockPurchaseAmount;
}

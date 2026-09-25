package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Table(name = "stock_and_purchase", indexes = {
        @Index(
                name = "idx_stock_and_purchase_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_stock_and_purchase_active",
                columnList = "is_active"
        )
})
public class StockAndPurchase extends TenantEntity {
    @Column(name = "payment_status")
    private String paymentStatus="NOT PAID";

    @Column(name = "payed_amount")
    private Integer payedAmount=0;

    @Column(name = "total_amount")
    private Integer totalAmount=0;

    @Column(name = "remaining_amount")
    private Integer remainingAmount=0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_uid")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Commission commission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_service")
    @JsonIgnore
    private BarServiceEntity barService;

    @Column(name = "week_date")
    private LocalDate weekDate;

    @PrePersist
    public void setWeekDate() {
        if (this.weekDate == null) {
            this.weekDate = LocalDate.now()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
    }
    @OneToMany(
            mappedBy = "stockAndPurchase",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL
    )
    @JsonIgnore
    private List<StockAndPurchaseDescriptions> descriptions = new ArrayList<>();

}

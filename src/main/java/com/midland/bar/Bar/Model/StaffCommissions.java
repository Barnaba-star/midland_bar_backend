package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
@Table(name = "staff_commissions",  indexes = {
        @Index(
                name = "idx_staff_commissions_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_staff_commissions_active",
                columnList = "is_active"
        )
})
public class StaffCommissions extends TenantEntity {
    @Column(name = "payment_status")
    private String paymentStatus="NOT PAID";

    @Column(name = "payed_amount")
    private Integer payedAmount=0;

    @Column(name = "total_amount")
    private Integer totalAmount=0;

    @Column(name = "remaining_amount")
    private Integer remainingAmount=0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_staff_uid")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private BarStaff barStaff;

    @Column(name = "week_date")
    private LocalDate weekDate;

    @PrePersist
    public void setWeekDate() {
        if (this.weekDate == null) {
            this.weekDate = LocalDate.now()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
    }

}

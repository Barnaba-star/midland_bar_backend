package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "bar_sales", indexes = {
        @Index(
                name = "idx_bar_sales_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_bar_sales_active",
                columnList = "is_active"
        )
})
public class BarSales extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_staff_uid")
    private BarStaff barStaff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_service_uid")
    private BarServiceEntity barServiceEntity;

    @Column(name = "payment_method")
    private String paymentMethod;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_opened")
    private SalesOpened salesOpened;





    @Column(name = "status")
    private String status = "ACTIVE";
}

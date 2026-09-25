package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "sales_opened", indexes = {
        @Index(
                name = "idx_sales_open_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_sales_open_active",
                columnList = "is_active"
        )
})
public class SalesOpened extends TenantEntity {

    @Column(name = "sales_code")
    private String salesCode;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "status")
    private String status = "ACTIVE";

    @Column(name = "payment_status")
    private String paymentStatus="PENDING";

    @Column(name = "paid_amount")
    private Integer paidAmount= 0;

    @Column(name = "bill")
    private Integer bill=0;
}

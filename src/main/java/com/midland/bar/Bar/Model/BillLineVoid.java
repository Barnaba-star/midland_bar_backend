package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Something taken off an unpaid bill (a Castle that should have been a
 * Serengeti). The sale is undone - stock, split, pots, commission, bill - and
 * this row keeps who did it, when, what, and why, for the CEO and manager.
 */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "bill_line_voids", indexes = {
        @Index(name = "idx_bill_line_void_branch_at", columnList = "branch_uid, voided_at")
})
public class BillLineVoid extends TenantEntity {

    @Column(name = "bill_uid", nullable = false)
    private String billUid;

    @Column(name = "sales_code")
    private String salesCode;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price")
    private Integer unitPrice;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    /** Whose sale it was - the staff member the line was sold as. */
    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "sold_at")
    private LocalDateTime soldAt;

    @Column(name = "reason", length = 300, nullable = false)
    private String reason;

    /** The login that took it off. */
    @Column(name = "voided_by", nullable = false)
    private String voidedBy;

    @Column(name = "voided_by_name")
    private String voidedByName;

    @Column(name = "voided_at", nullable = false)
    private LocalDateTime voidedAt;
}

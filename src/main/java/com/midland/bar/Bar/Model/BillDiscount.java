package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A discount given on an open bill: how much, why, by whom (owner, 2026-10-18). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "bill_discounts", indexes = @Index(name = "idx_bill_discount_branch_at", columnList = "branch_uid, given_at"))
public class BillDiscount extends TenantEntity {

    @Column(name = "bill_uid", nullable = false)
    private String billUid;

    @Column(name = "sales_code", length = 40)
    private String salesCode;

    /** The bill before the discount. */
    @Column(name = "bill_total", nullable = false)
    private Integer billTotal;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "reason", length = 300, nullable = false)
    private String reason;

    @Column(name = "given_by", nullable = false)
    private String givenBy;

    @Column(name = "given_by_name")
    private String givenByName;

    @Column(name = "given_at", nullable = false)
    private LocalDateTime givenAt;
}

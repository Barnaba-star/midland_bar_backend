package com.midland.bar.Bar.Supplier;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An order placed with a supplier: OPEN until everything on it arrived
 * (PARTIAL in between) or it is CANCELLED. Receiving against it raises the
 * store like any delivery and puts what arrived on the supplier's account.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "purchase_orders", indexes = @Index(name = "idx_po_branch", columnList = "branch_uid, ordered_at"))
public class PurchaseOrder extends TenantEntity {

    public static final String OPEN = "OPEN";
    public static final String PARTIAL = "PARTIAL";
    public static final String RECEIVED = "RECEIVED";
    public static final String CANCELLED = "CANCELLED";

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "supplier_uid", nullable = false)
    private String supplierUid;

    @Column(name = "status", nullable = false, length = 10)
    private String status = OPEN;

    @Column(name = "expected_on")
    private LocalDate expectedOn;

    @Column(name = "note", length = 300)
    private String note;

    /** What it comes to at the ordered prices. */
    @Column(name = "total")
    private Long total;

    /** Placed above what the products' Manunuzi allowed (a CEO or manager chose to). */
    @Column(name = "over_budget")
    private Boolean overBudget;

    @Column(name = "ordered_by")
    private String orderedBy;

    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;
}

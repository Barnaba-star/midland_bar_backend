package com.midland.bar.Bar.Supplier;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A line of what the bar owes a supplier: DELIVERY (goods arrived, owed more)
 * or PAYMENT (paid, owed less). A payment out of the drawer (fromDrawer)
 * comes off the payer's cash-up by its method.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "supplier_entries", indexes = {
        @Index(name = "idx_supplier_entry_supplier", columnList = "supplier_uid, entry_at"),
        @Index(name = "idx_supplier_entry_by", columnList = "entry_by, entry_at")
})
public class SupplierEntry extends TenantEntity {

    public static final String DELIVERY = "DELIVERY";
    public static final String PAYMENT = "PAYMENT";

    @Column(name = "supplier_uid", nullable = false)
    private String supplierUid;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "po_code", length = 20)
    private String poCode;

    /** DELIVERY: the stock receipt(s) it came from, e.g. "Castle Lite x 5". */
    @Column(name = "detail", length = 300)
    private String detail;

    @Column(name = "method", length = 20)
    private String method;

    @Column(name = "from_drawer")
    private Boolean fromDrawer;

    @Column(name = "entry_by", nullable = false)
    private String entryBy;

    @Column(name = "entry_by_name")
    private String entryByName;

    @Column(name = "entry_at", nullable = false)
    private LocalDateTime entryAt;
}

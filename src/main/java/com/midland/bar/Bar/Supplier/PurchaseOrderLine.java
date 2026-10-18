package com.midland.bar.Bar.Supplier;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One product on an order: how many packs (crates, cartons) at what price, and how many arrived. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "purchase_order_lines", indexes = @Index(name = "idx_po_line_po", columnList = "po_uid"))
public class PurchaseOrderLine extends TenantEntity {

    @Column(name = "po_uid", nullable = false)
    private String poUid;

    @Column(name = "service_uid", nullable = false)
    private String serviceUid;

    @Column(name = "service_name", length = 120)
    private String serviceName;

    @Column(name = "packs", nullable = false)
    private Integer packs;

    @Column(name = "pack_price", nullable = false)
    private Integer packPrice;

    @Column(name = "received_packs", nullable = false)
    private Integer receivedPacks = 0;
}

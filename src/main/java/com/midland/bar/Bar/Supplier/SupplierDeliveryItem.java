package com.midland.bar.Bar.Supplier;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One product's part of a delivery from a supplier: what it cost and how much
 * of it is paid. Paying a supplier settles these oldest first, and each part
 * is taken from that product's Manunuzi (Stock Purchase) savings.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "supplier_delivery_items", indexes = {
        @Index(name = "idx_sdi_supplier", columnList = "supplier_uid, received_at"),
        @Index(name = "idx_sdi_service", columnList = "service_uid")
})
public class SupplierDeliveryItem extends TenantEntity {

    @Column(name = "entry_uid", nullable = false)
    private String entryUid;

    @Column(name = "supplier_uid", nullable = false)
    private String supplierUid;

    @Column(name = "service_uid", nullable = false)
    private String serviceUid;

    @Column(name = "service_name", length = 120)
    private String serviceName;

    @Column(name = "po_code", length = 20)
    private String poCode;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "paid", nullable = false)
    private Long paid = 0L;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}

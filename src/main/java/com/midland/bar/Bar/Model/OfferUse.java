package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Part of a bill given free to a customer out of their monthly offer (ofa,
 * owner 2026-10-19). The bar's gift - never owed back, never in a cash-up.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "offer_uses", indexes = {
        @Index(name = "idx_offer_use_customer", columnList = "customer_uid, used_at"),
        @Index(name = "idx_offer_use_branch", columnList = "branch_uid, used_at")
})
public class OfferUse extends TenantEntity {

    @Column(name = "customer_uid", nullable = false)
    private String customerUid;

    @Column(name = "customer_name", length = 80)
    private String customerName;

    @Column(name = "bill_uid", nullable = false)
    private String billUid;

    @Column(name = "bill_code", length = 40)
    private String billCode;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "used_by", nullable = false)
    private String usedBy;

    @Column(name = "used_by_name")
    private String usedByName;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;
}

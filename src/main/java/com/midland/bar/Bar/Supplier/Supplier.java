package com.midland.bar.Bar.Supplier;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Who the bar buys from (owner, 2026-10-19). What is owed to them is their SupplierEntry lines. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "suppliers", indexes = @Index(name = "idx_supplier_branch", columnList = "branch_uid"))
public class Supplier extends TenantEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "contact_person", length = 80)
    private String contactPerson;

    @Column(name = "note", length = 300)
    private String note;
}

package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A customer the bar lets drink on credit (owner, 2026-10-18). The bar
 * carries the debt, not the staff member who served. A cashier may put a
 * bill on their account up to the limit a CEO or manager set; 0 means no
 * limit has been set yet, so nothing goes on credit.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "debt_customers", indexes = {
        @Index(name = "idx_debt_customer_branch", columnList = "branch_uid"),
        @Index(name = "idx_debt_customer_phone", columnList = "branch_uid, phone")
})
public class DebtCustomer extends TenantEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** 255XXXXXXXXX - one customer per number in a branch; reminders go here. */
    @Column(name = "phone", nullable = false, length = 15)
    private String phone;

    /** The most they may owe at once, in TZS. Set by a CEO or manager only. */
    @Column(name = "credit_limit", nullable = false)
    private Integer creditLimit = 0;

    /** Drinks the bar gives this customer free each month (ofa), in TZS; 0 = none. Set by a CEO or manager. */
    @Column(name = "monthly_offer")
    private Integer monthlyOffer;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "registered_by")
    private String registeredBy;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "limit_set_by")
    private String limitSetBy;

    @Column(name = "limit_set_at")
    private LocalDateTime limitSetAt;
}

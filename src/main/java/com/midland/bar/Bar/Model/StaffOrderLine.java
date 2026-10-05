package com.midland.bar.Bar.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One item on a staff order: a service and how many. Name and price are copied for showing; the sale charges the price of the day it is received. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "staff_order_lines")
public class StaffOrderLine extends TenantEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_order_uid", nullable = false)
    private StaffOrder order;

    @Column(name = "bar_service_uid", nullable = false)
    private String barServiceUid;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price")
    private Integer unitPrice;

    /** How the customer wants a drink: COLD or WARM; null = not said. For the supervisor to have it ready. */
    @Column(name = "serving")
    private String serving;

    /** COLD / WARM as sent by a screen ("cold", " Warm "), anything else = not said. */
    public static String serving(String raw) {
        if (raw == null)
            return null;
        String v = raw.trim().toUpperCase();
        return "COLD".equals(v) || "WARM".equals(v) ? v : null;
    }
}

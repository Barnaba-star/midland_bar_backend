package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * A happy hour or offer (owner, 2026-10-18): on the days and between the
 * times it runs, a product (or every product of a category) sells for less -
 * PERCENT off, a set PRICE, or BUY_GET (buy N, get M free on the same bill).
 * Applied as the line is sold; the split follows the price actually charged.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "price_rules", indexes = @Index(name = "idx_price_rule_branch", columnList = "branch_uid"))
public class PriceRule extends TenantEntity {

    public static final String PERCENT = "PERCENT";
    public static final String PRICE = "PRICE";
    public static final String BUY_GET = "BUY_GET";

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    /** One product, or null with a category (or neither = everything). */
    @Column(name = "service_uid")
    private String serviceUid;

    @Column(name = "category", length = 20)
    private String category;

    @Column(name = "percent")
    private Integer percent;

    @Column(name = "price")
    private Integer price;

    @Column(name = "buy_qty")
    private Integer buyQty;

    @Column(name = "free_qty")
    private Integer freeQty;

    /** ISO weekdays it runs on, e.g. "12345" for Monday-Friday. */
    @Column(name = "days", length = 7, nullable = false)
    private String days = "1234567";

    /** From and to; a "to" before "from" runs past midnight (22:00-02:00). */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}

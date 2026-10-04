package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

/** One product of a stock take, with its name and pack as they were that day. */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "stock_take_lines", indexes = {
        @Index(name = "idx_stock_take_line_parent", columnList = "stock_take_uid"),
        @Index(name = "idx_stock_take_line_service", columnList = "bar_service_uid")
})
public class StockTakeLine extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_take_uid", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private StockTake stockTake;

    @Column(name = "bar_service_uid", nullable = false)
    private String barServiceUid;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_code")
    private String serviceCode;

    @Column(name = "unit")
    private String unit;

    @Column(name = "pack_unit")
    private String packUnit;

    @Column(name = "units_per_pack")
    private Integer unitsPerPack;

    /** What the system held, in the smallest unit. */
    @Column(name = "system_units")
    private Integer systemUnits;

    @Column(name = "counted_units")
    private Integer countedUnits;

    /** Counted minus system: below zero is missing. */
    @Column(name = "difference_units")
    private Integer differenceUnits;

    /** The difference at buying price. */
    @Column(name = "difference_value")
    private Long differenceValue;
}

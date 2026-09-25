package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Table(name = "stock_and_purchase_descriptions",  indexes = {
        @Index(
                name = "idx_stock_and_purchase_descriptions_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_stock_and_purchase_descriptions_active",
                columnList = "is_active"
        )
})
public class StockAndPurchaseDescriptions extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_and_purchase")
    @JsonIgnore
    private StockAndPurchase stockAndPurchase;

    @Column(name = "descriptions")
    private String descriptions;

    @Column(name = "week_date")
    private LocalDate weekDate;

    @Column(name = "amount")
    private Integer amount;
}

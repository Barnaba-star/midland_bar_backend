package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "store_open",  indexes = {
        @Index(
                name = "idx_store_open_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_store_open_active",
                columnList = "is_active"
        )
})
public class StoreOpen extends TenantEntity {
    @Column(name = "open_quantity_code")
    private String openStoreCode;

    @Column(name = "open_quantity")
    private Integer openQuantity;

    @Column(name = "close_date")
    private LocalDate closeDate;

    @Column(name = "status")
    private String status="OPEN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_uid")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Store store;
}

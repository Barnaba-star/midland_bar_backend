package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
@Table(name = "store",  indexes = {
        @Index(
                name = "idx_store_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_store_active",
                columnList = "is_active"
        )
})
public class Store extends TenantEntity {
    @Column(name = "name_of_store")
    private String nameOfStore;

    @Column(name = "code_of_store")
    private String codeOfStore;

    @Column(name = "quantity")
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_service_entity_uid")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private BarServiceEntity barServiceEntity;

    @Column(name = "description")
    private String description;

    @Column(name = "status")
    private String status="AVL";

    @Column(name = "buying_price")
    private Integer buyingPrice;

    @Column(name = "total_quantity_price")
    private Integer totalQuantityPrice;

    @Column(name = "used_quantity")
    private Integer usedQuantity = 0;

    @Column(name = "not_used_quantity")
    private Integer notUsedQuantity;

}

package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "service_store_report", indexes = {
        @Index(
                name = "idx_service_store_report_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_service_store_report_active",
                columnList = "is_active"
        )
})
public class ServiceAndStoreReport extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bar_report_uid")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private BarReports barReports;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_open_uid")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StoreOpen storeOpen;

    @Column(name = "shared_amount")
    private Integer sharedAmount;
}

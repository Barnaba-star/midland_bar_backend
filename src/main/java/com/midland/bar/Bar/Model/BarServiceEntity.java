package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
@Table(name = "bar_services", indexes = {
        @Index(
                name = "idx_bar_services_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_bar_services_active",
                columnList = "is_active"
        )
})
public class BarServiceEntity extends TenantEntity {
    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "service_code")
    private String serviceCode;

    @Column(name = "description")
    private String description;

    @Column(name = "price")
    private Integer price;

    @Column(name = "duration")
    private Integer duration;


    @Column(name = "status")
    private String status;

    @Column(name = "usage_type")
    private String usageType;

}

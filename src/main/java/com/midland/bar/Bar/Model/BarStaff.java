package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.EnumNaming;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "bar_Staffs", indexes = {
        @Index(
                name = "idx_bar_Staffs_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_bar_Staffs_active",
                columnList = "is_active"
        )
})
public class BarStaff extends TenantEntity {
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "middle_name")
    private String middleName;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "phone_number")
    private String phoneNumber;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(name = "description")
    private String description;
    @Column(name = "bar_category")
    private String barCategory;
    @Column(name = "gender")
    private String gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_open")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StoreOpen storeOpen;
}

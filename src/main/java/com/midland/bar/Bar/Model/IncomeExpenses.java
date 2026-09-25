package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "income_expenses",

        indexes = {
                @Index(
                        name = "idx_income_expenses_branch",
                        columnList = "branch_uid"
                ),
                @Index(
                        name = "idx_income_expenses_active",
                        columnList = "is_active"
                )
        },

        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_income_expenses_name_branch_week",
                        columnNames = {
                                "name",
                                "branch_uid",
                                "week_start_date"
                        }
                )
        }
)

public class IncomeExpenses extends TenantEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(
            name = "income",
            precision = 15,
            scale = 2
    )
    private BigDecimal income = BigDecimal.ZERO;

    @Column(
            name = "expenses",
            precision = 15,
            scale = 2
    )
    private BigDecimal expenses = BigDecimal.ZERO;

    @Column(
            name = "descriptions",
            length = 500
    )
    private String descriptions;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(
            name = "week_start_date",
            nullable = false
    )
    private LocalDate weekStartDate;

    @OneToMany(
            mappedBy = "incomeExpenses",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JsonManagedReference
    private List<IncomeExpensesDescription> spends = new ArrayList<>();
}

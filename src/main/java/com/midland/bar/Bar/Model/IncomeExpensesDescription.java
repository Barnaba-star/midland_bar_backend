package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString(exclude = "incomeExpenses")
@Table(name = "income_expenses_description",    indexes = {
        @Index(
                name = "idx_income_expenses_description_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_income_expenses_description_active",
                columnList = "is_active"
        )
})
public class IncomeExpensesDescription extends TenantEntity {

    @Column(
            name = "description",
            nullable = false,
            length = 500
    )
    private String description;


    @Column(name = "description_date", nullable = false)
    private LocalDate descriptionDate;


    @Column(
            name = "spend_amount",
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal spendAmount;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "income_expenses_uid",
            nullable = false
    )
    @JsonBackReference
    private IncomeExpenses incomeExpenses;

    @Column(name = "staff_name")
    private String staffName;
}


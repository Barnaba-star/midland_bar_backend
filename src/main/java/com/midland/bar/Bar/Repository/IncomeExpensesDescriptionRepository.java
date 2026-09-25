package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.IncomeExpensesDescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomeExpensesDescriptionRepository extends JpaRepository<IncomeExpensesDescription, String> {
}

package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.IncomeExpensesDescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomeExpensesDescriptionRepository extends JpaRepository<IncomeExpensesDescription, String> {

    /** What one login paid out in [from, to) - pot name, description, who was paid, amount, when, method. */
    @org.springframework.data.jpa.repository.Query("SELECT d.incomeExpenses.name, d.description, d.staffName, d.spendAmount, d.paidAt, d.method FROM IncomeExpensesDescription d " +
           "WHERE d.branchUid = :branchUID AND d.paidBy = :email AND d.isActive = true AND d.paidAt >= :from AND d.paidAt < :to ORDER BY d.paidAt")
    java.util.List<Object[]> payoutsOf(@org.springframework.data.repository.query.Param("branchUID") String branchUID,
                                       @org.springframework.data.repository.query.Param("email") String email,
                                       @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                                       @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);
}

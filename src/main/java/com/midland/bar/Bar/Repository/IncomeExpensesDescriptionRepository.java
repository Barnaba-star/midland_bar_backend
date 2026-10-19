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

    /** Every pot's payouts made in [from, to) in a branch, with their pot - the reports place them by when they were paid. */
    @org.springframework.data.jpa.repository.Query("SELECT d FROM IncomeExpensesDescription d JOIN FETCH d.incomeExpenses ie " +
           "WHERE ie.branchUid = :branchUID AND d.isActive = true AND ((d.paidAt >= :from AND d.paidAt < :to) " +
           "OR (d.paidAt IS NULL AND d.descriptionDate >= :fromDay AND d.descriptionDate < :toDay))")
    java.util.List<IncomeExpensesDescription> paidBetween(@org.springframework.data.repository.query.Param("branchUID") String branchUID,
                                                          @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                                                          @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
                                                          @org.springframework.data.repository.query.Param("fromDay") java.time.LocalDate fromDay,
                                                          @org.springframework.data.repository.query.Param("toDay") java.time.LocalDate toDay);
}

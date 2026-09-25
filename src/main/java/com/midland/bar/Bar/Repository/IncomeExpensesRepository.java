package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.IncomeExpenses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IncomeExpensesRepository
        extends JpaRepository<IncomeExpenses, String> {

        @Query("""
            SELECT ie
            FROM IncomeExpenses ie
            WHERE ie.name = :name
              AND ie.branchUid = :branchUID
              AND ie.isActive = true
              AND ie.weekStartDate = :weekStartDate
            """)
        Optional<IncomeExpenses> findByNameAndWeek(
                @Param("name") String name,
                @Param("branchUID") String branchUID,
                @Param("weekStartDate") LocalDate weekStartDate
        );

        @Query("""
        SELECT ie
        FROM IncomeExpenses ie
        WHERE ie.branchUid = :branchUid
          AND ie.weekStartDate BETWEEN :startDate AND :endDate
        ORDER BY ie.weekStartDate ASC, ie.name ASC
        """)
        List<IncomeExpenses> findByBranchAndWeekRange(
                @Param("branchUid") String branchUid,
                @Param("startDate") LocalDate startDate,
                @Param("endDate") LocalDate endDate
        );
        @Query("""
    SELECT DISTINCT ie
    FROM IncomeExpenses ie
    LEFT JOIN FETCH ie.spends
    WHERE ie.uid = :uid
    AND ie.branchUid = :branchUID
""")
        Optional<IncomeExpenses> findIncomeExpensesAndDescription(
                @Param("uid") String uid,
                @Param("branchUID") String branchUID
        );

        @Query("""
    SELECT ie
    FROM IncomeExpenses ie
    WHERE ie.branchUid = :branchUid
      AND ie.weekStartDate=:weekDate AND ie.name='Staff'
    ORDER BY ie.weekStartDate ASC, ie.name ASC
""")
        List<IncomeExpenses> findByBranchAndWeekStartDateBetween(
                @Param("branchUid") String branchUid,
                @Param("weekDate") LocalDate weekDate
        );

        @Query("SELECT ic FROM IncomeExpenses ic WHERE ic.branchUid=:branchUID AND ic.weekStartDate=:weekDate AND ic.name='Stock Purchase'")
        Optional<IncomeExpenses> findIncomeAndExpensesByWeekDate(@Param("branchUID")String branchUID, LocalDate weekDate);

}

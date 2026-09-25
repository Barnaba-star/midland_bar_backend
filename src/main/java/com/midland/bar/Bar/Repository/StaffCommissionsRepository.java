package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.StaffCommissions;
import com.midland.bar.Bar.Projection.BarProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffCommissionsRepository extends JpaRepository<StaffCommissions, String> {


    @Query("""
                SELECT s
                FROM StaffCommissions s
                WHERE s.barStaff.uid = :staffUid
                  AND s.branchUid = :branchUid
                  AND s.createdAt = :date
            """)
    Optional<StaffCommissions> findCommission(@Param("staffUid") String staffUid, @Param("branchUid") String branchUid, @Param("date") LocalDate date);

    @Query("""
    SELECT
        s.uid AS uid,
        s.totalAmount AS totalAmount,
        s.payedAmount AS paidAmount,
        s.remainingAmount AS remainingAmount,
        s.createdAt AS date,
        s.barStaff.firstName AS firstName,
        s.barStaff.middleName AS middleName,
        s.barStaff.lastName AS lastName,
        s.barStaff.barCategory AS barCategory,
        s.weekDate AS weekDate,

        CASE
            WHEN s.remainingAmount = 0
                THEN 'PAID'
            WHEN s.payedAmount = 0
                THEN 'NOT PAID'
            ELSE 'PARTIAL'
        END AS status

    FROM StaffCommissions s

    WHERE s.branchUid = :branchUid
      AND s.createdAt >= :startDate
      AND s.createdAt <= :endDate

    ORDER BY s.barStaff.firstName ASC
""")
    Page<BarProjection> findStaffCommissionPage(
            Pageable pageable,
            @Param("branchUid") String branchUid,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate

    );


    @Query("""
    SELECT s
    FROM StaffCommissions s

    WHERE s.branchUid = :branchUid
      AND s.createdAt >= :startDate
      AND s.createdAt <= :endDate
      AND s.barStaff.firstName = :firstName
      AND s.barStaff.middleName = :middleName
      AND s.barStaff.lastName = :lastName

    ORDER BY s.createdAt DESC
""")
    List<StaffCommissions> findStaffCommissionListForPayment(
            @Param("branchUid") String branchUid,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("firstName") String firstName,
            @Param("middleName") String middleName,
            @Param("lastName") String lastName
    );

}



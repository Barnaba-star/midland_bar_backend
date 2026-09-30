package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BillPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, String> {

    @Query("SELECT p FROM BillPayment p WHERE p.salesOpened.uid = :billUid AND p.branchUid = :branchUID ORDER BY p.receivedAt")
    List<BillPayment> findByBill(@Param("billUid") String billUid, @Param("branchUID") String branchUID);

    /**
     * Money taken in a time window, per staff member and payment method:
     * [staffCode, staffName, method, amount, bills]. Bills opened on the Sales
     * page come back with a null staff code.
     */
    @Query("""
            SELECT p.salesOpened.staffCode, p.salesOpened.staffName, p.method, SUM(p.amount), COUNT(DISTINCT p.salesOpened.uid)
            FROM BillPayment p
            WHERE p.branchUid = :branchUID AND p.receivedAt >= :from AND p.receivedAt < :to
            GROUP BY p.salesOpened.staffCode, p.salesOpened.staffName, p.method
            """)
    List<Object[]> takingsByStaffAndMethod(@Param("branchUID") String branchUID,
                                           @Param("from") java.time.LocalDateTime from,
                                           @Param("to") java.time.LocalDateTime to);
}

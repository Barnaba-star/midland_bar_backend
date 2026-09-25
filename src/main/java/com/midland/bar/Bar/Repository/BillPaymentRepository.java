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
}

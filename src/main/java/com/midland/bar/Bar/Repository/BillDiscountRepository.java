package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BillDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BillDiscountRepository extends JpaRepository<BillDiscount, String> {

    @Query("SELECT d FROM BillDiscount d WHERE d.isActive = true AND d.branchUid = :branchUID AND d.givenAt >= :from AND d.givenAt < :to ORDER BY d.givenAt DESC")
    List<BillDiscount> between(@Param("branchUID") String branchUID, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM BillDiscount d WHERE d.isActive = true AND d.billUid = :billUid")
    long totalOnBill(@Param("billUid") String billUid);
}

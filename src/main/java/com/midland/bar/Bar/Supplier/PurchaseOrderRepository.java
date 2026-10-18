package com.midland.bar.Bar.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {

    @Query("SELECT p FROM PurchaseOrder p WHERE p.branchUid = :b ORDER BY p.orderedAt DESC")
    List<PurchaseOrder> findInBranch(@Param("b") String branchUID);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PurchaseOrder p WHERE p.uid = :uid AND p.branchUid = :b")
    Optional<PurchaseOrder> findForUpdate(@Param("uid") String uid, @Param("b") String branchUID);

    @Query("SELECT COUNT(p) FROM PurchaseOrder p WHERE p.branchUid = :b")
    long countInBranch(@Param("b") String branchUID);
}

package com.midland.bar.Bar.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, String> {

    @Query("SELECT l FROM PurchaseOrderLine l WHERE l.isActive = true AND l.poUid = :po ORDER BY l.serviceName")
    List<PurchaseOrderLine> findByPo(@Param("po") String poUid);

    /** [serviceUid, value still to come] on open and part-arrived orders of a branch. */
    @Query("SELECT l.serviceUid, COALESCE(SUM((l.packs - l.receivedPacks) * l.packPrice), 0) FROM PurchaseOrderLine l, PurchaseOrder p " +
           "WHERE l.isActive = true AND p.isActive = true AND p.uid = l.poUid AND p.branchUid = :b AND p.status IN ('OPEN', 'PARTIAL') GROUP BY l.serviceUid")
    List<Object[]> openByService(@Param("b") String branchUID);
}

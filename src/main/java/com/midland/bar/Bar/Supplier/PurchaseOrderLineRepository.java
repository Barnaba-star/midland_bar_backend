package com.midland.bar.Bar.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, String> {

    @Query("SELECT l FROM PurchaseOrderLine l WHERE l.poUid = :po ORDER BY l.serviceName")
    List<PurchaseOrderLine> findByPo(@Param("po") String poUid);
}

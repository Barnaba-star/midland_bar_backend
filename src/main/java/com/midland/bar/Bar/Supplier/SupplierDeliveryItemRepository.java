package com.midland.bar.Bar.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierDeliveryItemRepository extends JpaRepository<SupplierDeliveryItem, String> {

    /** A supplier's delivered products not yet fully paid, oldest first. */
    @Query("SELECT i FROM SupplierDeliveryItem i WHERE i.isActive = true AND i.supplierUid = :s AND i.paid < i.amount ORDER BY i.receivedAt")
    List<SupplierDeliveryItem> unpaid(@Param("s") String supplierUid);

    /** [serviceUid, unpaid] - delivered, not yet paid, per product in a branch. */
    @Query("SELECT i.serviceUid, COALESCE(SUM(i.amount - i.paid), 0) FROM SupplierDeliveryItem i " +
           "WHERE i.isActive = true AND i.branchUid = :b AND i.paid < i.amount GROUP BY i.serviceUid")
    List<Object[]> unpaidByService(@Param("b") String branchUID);
}

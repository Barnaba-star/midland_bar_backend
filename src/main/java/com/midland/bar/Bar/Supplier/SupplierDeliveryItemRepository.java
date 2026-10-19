package com.midland.bar.Bar.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierDeliveryItemRepository extends JpaRepository<SupplierDeliveryItem, String> {

    long countByEntryUid(String entryUid);

    /** A supplier's delivered products not yet fully paid, oldest first. */
    @Query("SELECT i FROM SupplierDeliveryItem i WHERE i.isActive = true AND i.supplierUid = :s AND i.paid < i.amount ORDER BY i.receivedAt")
    List<SupplierDeliveryItem> unpaid(@Param("s") String supplierUid);

    /**
     * [serviceUid, unpaid] - delivered, not yet paid and not yet taken from
     * Manunuzi, per product in a branch (newer deliveries take theirs on arrival).
     */
    @Query("SELECT i.serviceUid, COALESCE(SUM(i.amount - i.paid), 0) FROM SupplierDeliveryItem i " +
           "WHERE i.isActive = true AND i.branchUid = :b AND i.paid < i.amount AND i.potCovered IS NULL GROUP BY i.serviceUid")
    List<Object[]> unpaidByService(@Param("b") String branchUID);

    /**
     * [received at, PO code, supplier, product, part beyond Manunuzi] - goods
     * received in [from, to) that their product's Manunuzi savings did not
     * fully cover, so the rest was paid from outside it.
     */
    @Query("SELECT i.receivedAt, i.poCode, s.name, i.serviceName, i.amount - i.potCovered FROM SupplierDeliveryItem i, Supplier s " +
           "WHERE s.uid = i.supplierUid AND i.isActive = true AND i.branchUid = :b AND i.potCovered IS NOT NULL AND i.potCovered < i.amount " +
           "AND i.receivedAt >= :from AND i.receivedAt < :to ORDER BY i.receivedAt DESC")
    List<Object[]> outsideBetween(@Param("b") String branchUID, @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to);
}

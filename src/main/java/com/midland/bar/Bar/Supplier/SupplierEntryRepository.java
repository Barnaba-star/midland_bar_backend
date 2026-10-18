package com.midland.bar.Bar.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SupplierEntryRepository extends JpaRepository<SupplierEntry, String> {

    /** [supplierUid, owed] for a branch. */
    @Query("SELECT e.supplierUid, COALESCE(SUM(CASE WHEN e.kind = 'DELIVERY' THEN e.amount ELSE -e.amount END), 0) " +
           "FROM SupplierEntry e WHERE e.isActive = true AND e.branchUid = :b GROUP BY e.supplierUid")
    List<Object[]> balances(@Param("b") String branchUID);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.kind = 'DELIVERY' THEN e.amount ELSE -e.amount END), 0) FROM SupplierEntry e WHERE e.isActive = true AND e.supplierUid = :s")
    long balanceOf(@Param("s") String supplierUid);

    @Query("SELECT e FROM SupplierEntry e WHERE e.isActive = true AND e.supplierUid = :s AND e.branchUid = :b ORDER BY e.entryAt DESC")
    List<SupplierEntry> ledger(@Param("s") String supplierUid, @Param("b") String branchUID);

    /** Supplier payments out of one login's drawer in [from, to) - off their cash-up. */
    @Query("SELECT e FROM SupplierEntry e WHERE e.isActive = true AND e.branchUid = :b AND e.entryBy = :email AND e.kind = 'PAYMENT' " +
           "AND e.fromDrawer = true AND e.entryAt >= :from AND e.entryAt < :to ORDER BY e.entryAt")
    List<SupplierEntry> paidFromDrawerBy(@Param("b") String branchUID, @Param("email") String email,
                                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

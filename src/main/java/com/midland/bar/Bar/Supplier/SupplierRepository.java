package com.midland.bar.Bar.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, String> {

    @Query("SELECT s FROM Supplier s WHERE s.branchUid = :b ORDER BY s.name")
    List<Supplier> findInBranch(@Param("b") String branchUID);

    @Query("SELECT s FROM Supplier s WHERE s.uid = :uid AND s.branchUid = :b")
    Optional<Supplier> findInBranch(@Param("uid") String uid, @Param("b") String branchUID);
}

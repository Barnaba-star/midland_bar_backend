package com.midland.bar.Bar.Table;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BarTableRepository extends JpaRepository<BarTable, String> {

    /** A branch's tables (removed ones are hidden by TenantEntity), in card order. */
    @Query("SELECT t FROM BarTable t WHERE t.branchUid = :branchUID AND t.isActive = true ORDER BY t.number")
    List<BarTable> findAllInBranch(@Param("branchUID") String branchUID);

    @Query("SELECT t FROM BarTable t WHERE t.uid = :uid AND t.branchUid = :branchUID AND t.isActive = true")
    Optional<BarTable> findInBranch(@Param("uid") String uid, @Param("branchUID") String branchUID);

    /** The customers' window: a live table by its QR token, any branch. */
    @Query("SELECT t FROM BarTable t WHERE t.token = :token AND t.isActive = true")
    Optional<BarTable> findByToken(@Param("token") String token);

    /** Every token ever handed out, removed tables included - never reuse one. */
    @Query(value = "SELECT COUNT(*) FROM bar_tables WHERE token = :token", nativeQuery = true)
    long countToken(@Param("token") String token);
}

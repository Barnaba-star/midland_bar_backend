package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BillCode;
import com.midland.bar.Bar.Projection.BillCodeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillCodeRepository extends JpaRepository<BillCode, String> {

    /*
     * A code is in use while a bill opened under it in this branch is still
     * unpaid. Paying the bill is what frees the code.
     */
    String IN_USE = """
            EXISTS (SELECT so FROM SalesOpened so
                    WHERE so.salesCode = c.code AND so.branchUid = c.branchUid
                      AND so.isActive = true
                      AND (so.paymentStatus IS NULL OR so.paymentStatus <> 'PAID'))
            """;

    @Query("SELECT c.uid AS uid, c.code AS code, CASE WHEN " + IN_USE + " THEN true ELSE false END AS inUse " +
           "FROM BillCode c WHERE c.branchUid = :branchUID ORDER BY c.code")
    List<BillCodeProjection> findAllWithUse(@Param("branchUID") String branchUID);

    @Query("SELECT c.code FROM BillCode c WHERE c.branchUid = :branchUID AND NOT " + IN_USE + " ORDER BY c.code")
    List<String> findAvailableCodes(@Param("branchUID") String branchUID);

    @Query("SELECT c FROM BillCode c WHERE c.branchUid = :branchUID AND UPPER(c.code) = UPPER(:code)")
    Optional<BillCode> findByCode(@Param("branchUID") String branchUID, @Param("code") String code);

    @Query("SELECT c FROM BillCode c WHERE c.uid = :uid AND c.branchUid = :branchUID")
    Optional<BillCode> findByUid(@Param("uid") String uid, @Param("branchUID") String branchUID);
}

package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.StockAdjustment;
import com.midland.bar.Bar.Projection.StockAdjustmentProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, String> {

    @Query("""
           SELECT a.uid AS uid, a.mode AS mode, a.reason AS reason, a.unitsChanged AS unitsChanged,
                  a.stockBefore AS stockBefore, a.stockAfter AS stockAfter, a.costValue AS costValue,
                  a.note AS note, a.adjustedBy AS adjustedBy, a.adjustedAt AS adjustedAt
           FROM StockAdjustment a
           WHERE a.branchUid = :branchUID AND a.barService.uid = :serviceUID
           ORDER BY a.adjustedAt DESC
           """)
    Page<StockAdjustmentProjection> findByService(@Param("branchUID") String branchUID,
                                                  @Param("serviceUID") String serviceUID,
                                                  Pageable pageable);
}

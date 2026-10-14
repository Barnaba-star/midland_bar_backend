package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BarSaleStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BarSaleStockRepository extends JpaRepository<BarSaleStock, String> {
    @Query("SELECT x FROM BarSaleStock x WHERE x.barSalesUid = :lineUid")
    List<BarSaleStock> findByLine(@Param("lineUid") String lineUid);
}

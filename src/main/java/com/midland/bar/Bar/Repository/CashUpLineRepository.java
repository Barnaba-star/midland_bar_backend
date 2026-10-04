package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.CashUpLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CashUpLineRepository extends JpaRepository<CashUpLine, String> {

    @Query("SELECT l FROM CashUpLine l WHERE l.cashUp.uid = :cashUpUid AND l.isActive = true ORDER BY l.expected DESC")
    List<CashUpLine> findByCashUp(@Param("cashUpUid") String cashUpUid);
}

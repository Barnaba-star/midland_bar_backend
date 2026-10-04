package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.OtherCommissionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OtherCommissionItemRepository extends JpaRepository<OtherCommissionItem, String> {

    @Query("SELECT o FROM OtherCommissionItem o WHERE o.branchUid = :branchUID AND o.isActive = true ORDER BY o.sortOrder, o.name")
    List<OtherCommissionItem> findActive(@Param("branchUID") String branchUID);
}

package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BarServiceEntity;
import com.midland.bar.Bar.Projection.BarProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BarServiceRepository extends JpaRepository<BarServiceEntity, String> {
    @Query("SELECT s FROM BarServiceEntity s WHERE s.uid=:barServiceUID AND s.branchUid=:branchUID")
    Optional<BarServiceEntity> findBarServiceByUID(@Param("barServiceUID") String barServiceUID, String branchUID);

    @Query("""
           SELECT s.uid AS uid, s.serviceName AS serviceName,
              s.serviceCode AS serviceCode,
              s.description AS description,
              s.price AS price,
              s.duration AS duration,
              s.status AS status,
              s.usageType AS usageType
           FROM BarServiceEntity s
           WHERE s.branchUid = :branchUID
           """)
    List<BarProjection> findAllBarServiceList(
            @Param("branchUID") String branchUID
    );

    @Query("""
           SELECT s.uid AS uid, s.serviceName AS serviceName,
              s.serviceCode AS serviceCode,
              s.description AS description,
              s.price AS price,
              s.duration AS duration,
              s.status AS status,
              s.usageType AS usageType
           FROM BarServiceEntity s
           WHERE s.branchUid = :branchUID
           """)
    Page<BarProjection> findBarServicePage(
            Pageable pageable, @Param("branchUID") String branchUID
    );
}

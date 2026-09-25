package com.midland.bar.Setting.Repository;

import com.midland.bar.Setting.Model.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegionRepository extends JpaRepository<Region, String> {

    @Query("SELECT r FROM Region r ORDER BY r.name ASC")
    List<Region> findAllOrdered();

    @Query("SELECT r FROM Region r WHERE UPPER(r.name) = UPPER(:name)")
    Optional<Region> findByName(@Param("name") String name);

    @Query("SELECT r FROM Region r WHERE UPPER(r.code) = UPPER(:code)")
    Optional<Region> findByCode(@Param("code") String code);
}

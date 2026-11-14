package com.midland.bar.Bar.Catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, String> {

    @Query("SELECT c FROM CatalogItem c WHERE c.isActive = true ORDER BY LOWER(c.name), LOWER(c.flavour), LOWER(c.size)")
    List<CatalogItem> findAllSorted();

    @Query("SELECT c FROM CatalogItem c WHERE c.isActive = true AND (c.barcode = :code OR c.packBarcode = :code)")
    List<CatalogItem> findByAnyBarcode(@Param("code") String code);
}

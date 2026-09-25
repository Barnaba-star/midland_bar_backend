package com.midland.bar.Uaa.Repository;

import com.midland.bar.Uaa.Model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    Optional<Permission> findByName(@Param("name") String name);

    List<Permission> findByModuleIgnoreCase(String module);

    @Query("SELECT DISTINCT p.module FROM Permission p")
    List<String> findDistinctModules();

    @Query("SELECT p FROM Permission p WHERE p.module=:moduleName")
    List<Permission> findPermissionByModuleName(String moduleName);

}

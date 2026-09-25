package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Projection.BarProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BarStaffRepository extends JpaRepository<BarStaff, String> {
    @Query("SELECT s FROM BarStaff s WHERE s.userUid=:userUid AND s.branchUid=:branchUID AND s.isActive=true")
    java.util.List<BarStaff> findByUserUid(String userUid, String branchUID);

    @Query("SELECT s FROM BarStaff s WHERE s.uid=:uid AND s.branchUid=:branchUID AND s.isActive=true")
    Optional<BarStaff> findBarStaffByUID(String uid, String branchUID);

    @Query("""
            SELECT s.uid as uid, s.firstName as firstName, s.middleName as middleName, s.lastName as lastName, s.dateOfBirth as dateOfBirth, s.phoneNumber as phoneNumber,
            s.description as description, s.gender as gender, s.barCategory as barCategory, s.isActive as active FROM BarStaff s  WHERE s.branchUid=:branchUID AND s.isActive=true
            """)
    List<BarProjection> findBarStaffList(String branchUID);


    @Query("""
             SELECT s.uid as uid, s.firstName as firstName, s.middleName as middleName, s.lastName as lastName, s.dateOfBirth as dateOfBirth, s.phoneNumber as phoneNumber,
             s.description as description, s.gender as gender, s.barCategory as barCategory, s.isActive as active FROM BarStaff s  WHERE s.branchUid=:branchUID AND s.isActive=true
               AND (:category = '' OR s.barCategory = :category)
               AND (LOWER(CONCAT(COALESCE(s.firstName, ''), ' ', COALESCE(s.middleName, ''), ' ', COALESCE(s.lastName, ''))) LIKE :search ESCAPE '\\'
                    OR LOWER(COALESCE(s.phoneNumber, '')) LIKE :search ESCAPE '\\')
             ORDER BY s.firstName, s.lastName
            """)
    Page<BarProjection> findBarStaffPage(Pageable pageable, String branchUID, String search, String category);
}

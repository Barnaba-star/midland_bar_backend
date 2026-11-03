package com.midland.bar.Bar.JackPoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JackPointAwardRepository extends JpaRepository<JackPointAward, String> {

    @Query("SELECT a FROM JackPointAward a WHERE a.branchUid = :branchUID AND a.isActive = true ORDER BY a.qualifiedAt DESC")
    List<JackPointAward> findOfBranch(@Param("branchUID") String branchUID);

    @Query("SELECT a FROM JackPointAward a WHERE a.jackPointUid = :jackPointUid AND a.isActive = true")
    List<JackPointAward> findOfJackPoint(@Param("jackPointUid") String jackPointUid);

    @Query("SELECT a FROM JackPointAward a WHERE a.staffUid = :staffUid AND a.branchUid = :branchUID AND a.isActive = true ORDER BY a.qualifiedAt DESC")
    List<JackPointAward> findOfStaff(@Param("staffUid") String staffUid, @Param("branchUID") String branchUID);

    @Query("SELECT a FROM JackPointAward a WHERE a.uid = :uid AND a.branchUid = :branchUID AND a.isActive = true")
    Optional<JackPointAward> findOne(@Param("uid") String uid, @Param("branchUID") String branchUID);
}

package com.midland.bar.Bar.JackPoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JackPointRepository extends JpaRepository<JackPoint, String> {

    @Query("SELECT j FROM JackPoint j WHERE j.branchUid = :branchUID AND j.isActive = true ORDER BY j.enabled DESC, j.startDate DESC")
    List<JackPoint> findOfBranch(@Param("branchUID") String branchUID);

    @Query("SELECT j FROM JackPoint j WHERE j.uid = :uid AND j.branchUid = :branchUID AND j.isActive = true")
    Optional<JackPoint> findOne(@Param("uid") String uid, @Param("branchUID") String branchUID);

    /** Branches with a game running, for the weekly check. */
    @Query("SELECT DISTINCT j.branchUid FROM JackPoint j WHERE j.isActive = true AND j.enabled = true")
    List<String> branchesWithGames();
}

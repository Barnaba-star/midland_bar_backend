package com.midland.bar.Bar.Repository;

import com.midland.bar.Bar.Model.BarStaff;
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
public interface BarStaffRepository extends JpaRepository<BarStaff, String> {
    @Query("SELECT s FROM BarStaff s WHERE s.userUid=:userUid AND s.branchUid=:branchUID AND s.isActive=true")
    java.util.List<BarStaff> findByUserUid(String userUid, String branchUID);

    @Query("SELECT s FROM BarStaff s WHERE s.uid=:uid AND s.branchUid=:branchUID AND s.isActive=true")
    Optional<BarStaff> findBarStaffByUID(String uid, String branchUID);

    @Query("""
            SELECT s.uid as uid, s.staffCode as staffCode, s.firstName as firstName, s.middleName as middleName, s.lastName as lastName, s.dateOfBirth as dateOfBirth, s.phoneNumber as phoneNumber,
            s.description as description, s.gender as gender, s.barCategory as barCategory, s.isActive as active FROM BarStaff s  WHERE s.branchUid=:branchUID AND s.isActive=true
            """)
    List<BarProjection> findBarStaffList(String branchUID);


    @Query("""
             SELECT s.uid as uid, s.staffCode as staffCode, s.firstName as firstName, s.middleName as middleName, s.lastName as lastName, s.dateOfBirth as dateOfBirth, s.phoneNumber as phoneNumber,
             s.description as description, s.gender as gender, s.barCategory as barCategory, s.isActive as active, s.userUid as userUid,
             (CASE WHEN s.pinHash IS NULL THEN false ELSE true END) as hasPin FROM BarStaff s  WHERE s.branchUid=:branchUID AND s.isActive=true
               AND (:category = '' OR s.barCategory = :category)
               AND (LOWER(CONCAT(COALESCE(s.firstName, ''), ' ', COALESCE(s.middleName, ''), ' ', COALESCE(s.lastName, ''))) LIKE :search ESCAPE '\\'
                    OR LOWER(COALESCE(s.phoneNumber, '')) LIKE :search ESCAPE '\\'
                    OR LOWER(COALESCE(s.staffCode, '')) LIKE :search ESCAPE '\\')
             ORDER BY s.firstName, s.lastName
            """)
    Page<BarProjection> findBarStaffPage(Pageable pageable, String branchUID, String search, String category);

    /** Active staff by the code they type at Staff Sell. */
    @Query("SELECT s FROM BarStaff s WHERE UPPER(s.staffCode) = UPPER(:code) AND s.branchUid = :branchUID AND s.isActive = true")
    Optional<BarStaff> findByStaffCode(@Param("code") String code, @Param("branchUID") String branchUID);

    /** Active staff holding a code in any branch - staff code sign-in without a registered device. */
    @Query("SELECT s FROM BarStaff s WHERE UPPER(s.staffCode) = UPPER(:code) AND s.isActive = true")
    java.util.List<BarStaff> findAllByStaffCodeAnyBranch(@Param("code") String code);

    /** Whoever holds a code, active or not - codes are never handed out twice. */
    @Query("SELECT s FROM BarStaff s WHERE UPPER(s.staffCode) = UPPER(:code) AND s.branchUid = :branchUID")
    Optional<BarStaff> findByStaffCodeAnyStatus(@Param("code") String code, @Param("branchUID") String branchUID);

    /** Every code the branch has handed out, active or not - codes are never reused. */
    @Query("SELECT s.staffCode FROM BarStaff s WHERE s.branchUid = :branchUID AND s.staffCode IS NOT NULL")
    List<String> findStaffCodes(@Param("branchUID") String branchUID);

    /** Staff from before codes existed, oldest first, so they number in the order they joined. */
    @Query("SELECT s FROM BarStaff s WHERE s.staffCode IS NULL ORDER BY s.branchUid, s.createdAt, s.uid")
    List<BarStaff> findWithoutCode();

    /** Every staff member that has a code, to find the typed ones (JAMES, K1) on startup. */
    @Query("SELECT s FROM BarStaff s WHERE s.staffCode IS NOT NULL ORDER BY s.branchUid, s.createdAt, s.uid")
    List<BarStaff> findWithCode();

    /** Unpaid bills plus undecided Staff Sell orders a staff member still has open. */
    @Query("""
            SELECT (SELECT COUNT(b) FROM SalesOpened b WHERE b.staffUid = :staffUid AND b.paymentStatus = 'PENDING')
                 + (SELECT COUNT(o) FROM StaffOrder o WHERE o.staffUid = :staffUid AND o.status IN ('DRAFT', 'SENT'))
            FROM BarStaff s WHERE s.uid = :staffUid
            """)
    long countOpenWork(@Param("staffUid") String staffUid);

    /**
     * Whether a code is held in the branch by anyone else - removed staff
     * included. Native SQL on purpose: the entity's is_active filter would
     * hide staff who left, and their codes are never handed out again.
     */
    @Query(value = "SELECT COUNT(*) FROM bar_staffs WHERE branch_uid = :branchUID AND UPPER(staff_code) = UPPER(:code) AND (:selfUid IS NULL OR uid <> :selfUid)", nativeQuery = true)
    long countCodeHolders(@Param("code") String code, @Param("branchUID") String branchUID, @Param("selfUid") String selfUid);

    /** Holders of a code in ANY branch (removed staff included): codes are unique across all branches. */
    @Query(value = "SELECT COUNT(*) FROM bar_staffs WHERE UPPER(staff_code) = UPPER(:code) AND (:selfUid IS NULL OR uid <> :selfUid)", nativeQuery = true)
    long countCodeHoldersAnyBranch(@Param("code") String code, @Param("selfUid") String selfUid);

    /** Every code ever given, in every branch. */
    @Query(value = "SELECT staff_code FROM bar_staffs WHERE staff_code IS NOT NULL", nativeQuery = true)
    List<String> findAllStaffCodesAnyBranch();

    /** Every code ever given in the branch, removed staff included (see countCodeHolders). */
    @Query(value = "SELECT staff_code FROM bar_staffs WHERE branch_uid = :branchUID AND staff_code IS NOT NULL", nativeQuery = true)
    List<String> findAllStaffCodes(@Param("branchUID") String branchUID);
}

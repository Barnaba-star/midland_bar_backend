package com.midland.bar.Uaa.Repository;

import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Projection.UserProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.username = :username")
    Boolean existByUsername(@Param("username") String username);

    /**
     * Whether any row at all holds this username.
     *
     * Native on purpose: User carries @Where(is_active = true), so the JPQL
     * version above cannot see a deactivated account - it would hand out a
     * username that is still taken in the table, and the unique constraint
     * would then reject the insert.
     */
    @Query(value = "SELECT COUNT(*) FROM users WHERE username = :username", nativeQuery = true)
    long countUsername(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE u.username=:username")
    User findByUsername(@Param("username") String username);

    /**
     * "First Middle Last" for a sign-in name or email, for printing who did
     * something (a receipt's "served by"). Native so a user who has since
     * been removed still prints by name on their old bills.
     */
    @Query(value = """
        SELECT TRIM(CONCAT_WS(' ', NULLIF(TRIM(first_name), ''), NULLIF(TRIM(middle_name), ''), NULLIF(TRIM(last_name), '')))
        FROM users
        WHERE LOWER(username) = LOWER(:login) OR LOWER(email) = LOWER(:login)
        ORDER BY (LOWER(username) = LOWER(:login)) DESC
        LIMIT 1
        """, nativeQuery = true)
    String findFullNameByLogin(@Param("login") String login);

    @Query("SELECT u FROM User u WHERE u.username=:username")
    User findFirstByUsername(@Param("username")String username);

    // The principal every authenticated request is built from. Branch and roles
    // come back in the same select so the filter does not trigger one lookup per
    // association; role permissions follow in a single batched select.
    @Query("""
    SELECT DISTINCT u
    FROM User u
    LEFT JOIN FETCH u.branch
    LEFT JOIN FETCH u.roles
    WHERE u.username = :username
""")
    User findByUsernameForAuthentication(@Param("username") String username);


    @Query("SELECT u FROM User u WHERE  u.isActive=true ")
    Page<User> findUserPage(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.isActive=true AND u.branch.uid=:branchUID")
    Page<User> findUserPageByBranch(Pageable pageable, String branchUID);


    @Query("""
    SELECT DISTINCT u
    FROM User u
    LEFT JOIN FETCH u.branch
    LEFT JOIN FETCH u.roles WHERE u.branch.uid=:branchUID
""")
    List<User> findAllUsersWithBranchAndRoles(@Param("branchUID")String branchUID);

    @Query("""
    SELECT
        u.uid AS uid,
        u.username AS username,
        u.firstName AS firstName,
        u.middleName AS middleName,
        u.lastName AS lastName,
        u.email AS email,
        u.phone AS phone,
        u.gender AS gender,
        u.dateOfBirth AS dateOfBirth,
        u.address AS address,
        u.isRoot AS isRoot,
        u.isBlocked AS isBlocked,
        u.isActive AS isActive,
        u.createdAt AS createdAt,
        u.updatedAt AS updatedAt,
        u.mustChangePassword AS mustChangePassword,
        u.activationExpiresAt AS activationExpiresAt,
        u.accountNumber AS accountNumber,
        u.bankName AS bankName,
        u.accountName AS accountName,

        b.uid AS branchUid,
        b.branchName AS branchName,
        b.branchCode AS branchCode

    FROM User u
    LEFT JOIN u.branch b
    WHERE (:search IS NULL
           OR LOWER(u.firstName) LIKE %:search%
           OR LOWER(u.middleName) LIKE %:search%
           OR LOWER(u.lastName) LIKE %:search%
           OR LOWER(u.username) LIKE %:search%
           OR LOWER(u.email) LIKE %:search%
           OR LOWER(u.phone) LIKE %:search%
           OR LOWER(b.branchName) LIKE %:search%)
""")
    Page<UserProjection> findUsers(
            @Param("search") String search,
            Pageable pageable
    );

    // Everyone holding a given role. The commission report starts from this
    // so a STAFF member shows up the day they are given the role, with zeros,
    // instead of only appearing once they have registered their first branch.
    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.code = :code")
    List<User> findAllByRoleCode(@Param("code") String code);

    @Modifying
    @Query(
            value = """
                DELETE FROM user_roles
                WHERE user_uid = :uid
                """,
            nativeQuery = true
    )
    void deleteUserRoles(@Param("uid") String uid);

    @Query("""
    SELECT
        u.uid AS uid,
        u.username AS username,
        u.firstName AS firstName,
        u.middleName AS middleName,
        u.lastName AS lastName,
        u.accountNumber AS accountNumber,
        u.bankName AS bankName,
        u.accountName AS accountName,

        r.name AS roleName,
        r.uid AS roleUID,
        r.code AS roleCode

    FROM User u
    LEFT JOIN u.roles r
    WHERE u.uid = :userUID
    ORDER BY CASE WHEN r.name = 'ROOT' THEN 1 ELSE 0 END, r.name
    """)
    // One row per role: root also holds DIRECTOR, and a single-result query
    // failed on them with "2 results were returned". The assignable role
    // (anything but ROOT) comes first.
    List<UserProjection> findUserWithRoles(
            @Param("userUID") String userUID
    );


    @Query("""
    SELECT
    u.firstName AS firstName,
    u.middleName AS middleName,
    u.lastName AS lastName,
    u.lastSeen AS lastSeen,
    u.email AS email,
    r.name AS roleName,
    r.uid AS roleUID,
    r.code AS roleCode,
    b.branchName AS branchName,
    b.branchCode AS branchCode
    FROM User u
    LEFT JOIN u.roles r
    LEFT JOIN u.branch b
    WHERE u.lastSeen >= :cutoffTime
    ORDER BY u.lastSeen DESC
""")
    List<UserProjection> findOnlineUsers(
            @Param("cutoffTime") LocalDateTime cutoffTime
    );




    /** [userUid, roleName] for a page of users - their roles, in one query. */
    @Query("SELECT u.uid, r.name FROM User u JOIN u.roles r WHERE u.uid IN :uids ORDER BY r.name")
    List<Object[]> findRoleNamesOf(@Param("uids") java.util.Collection<String> uids);
}

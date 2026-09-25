package com.midland.bar.Monitoring.Repository;

import com.midland.bar.Monitoring.Model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    // Every filter is optional; a null means "do not narrow on this".
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:username IS NULL OR LOWER(a.username) LIKE %:username%)
              AND (:outcome IS NULL OR a.outcome = :outcome)
              AND (:search IS NULL
                   OR LOWER(a.action) LIKE %:search%
                   OR LOWER(a.path) LIKE %:search%)
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditLog> findAuditPage(
            @Param("username") String username,
            @Param("outcome") String outcome,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.occurredAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.occurredAt < :cutoff")
    long countOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(DISTINCT a.username) FROM AuditLog a WHERE a.occurredAt >= :since")
    long countActiveUsersSince(@Param("since") LocalDateTime since);

    // Aged out rather than cleared by hand: an audit trail nobody can delete
    // from the screen is worth more than one that can be tidied away.
    @Modifying
    @Query(value = "DELETE FROM audit_logs WHERE occurred_at < :cutoff", nativeQuery = true)
    int purgeOlderThan(@Param("cutoff") LocalDateTime cutoff);
}

package com.midland.bar.Monitoring.Repository;

import com.midland.bar.Monitoring.Model.ErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ErrorLogRepository extends JpaRepository<ErrorLog, String> {

    // source and level are both optional - a null means "don't narrow on this".
    @Query("""
            SELECT e FROM ErrorLog e
            WHERE (:source IS NULL OR e.source = :source)
              AND (:level IS NULL OR e.level = :level)
            ORDER BY e.occurredAt DESC
            """)
    Page<ErrorLog> findErrorPage(
            @Param("source") String source,
            @Param("level") String level,
            Pageable pageable
    );

    @Query("SELECT COUNT(e) FROM ErrorLog e WHERE e.occurredAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    // Clearing the list is a soft delete, same as everywhere else in the app -
    // BaseEntity's @Where(is_active = true) hides them from every read above.
    @Modifying
    @Query("UPDATE ErrorLog e SET e.isActive = false, e.deletedAt = CURRENT_DATE WHERE e.isActive = true")
    int clearAll();

    @Modifying
    @Query("UPDATE ErrorLog e SET e.isActive = false, e.deletedAt = CURRENT_DATE WHERE e.isActive = true AND e.occurredAt < :cutoff")
    int clearOlderThan(@Param("cutoff") LocalDateTime cutoff);

    // Rows stay soft-deleted for a while so a mistaken "clear" is recoverable,
    // then go for good rather than growing the table forever.
    @Modifying
    @Query(value = "DELETE FROM error_logs WHERE is_active = false AND occurred_at < :cutoff", nativeQuery = true)
    int purgeOlderThan(@Param("cutoff") LocalDateTime cutoff);
}

package com.midland.bar.Notification.Sms.Repository;

import com.midland.bar.Notification.Sms.Model.SmsLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SmsLogRepository extends JpaRepository<SmsLog, String> {

    @Query("""
            SELECT s FROM SmsLog s
            WHERE (:status IS NULL OR s.status = :status)
            ORDER BY s.sentAt DESC
            """)
    Page<SmsLog> findSmsPage(@Param("status") String status, Pageable pageable);
}

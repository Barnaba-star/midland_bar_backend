package com.midland.bar.Monitoring.Model;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Who changed what, and when.
 *
 * Written by AuditLogFilter for every request that changes something, rather
 * than by each service calling out to record itself - an endpoint added next
 * month is covered without anyone remembering to wire it up.
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_audit_logs_username", columnList = "username"),
        @Index(name = "idx_audit_logs_branch", columnList = "branch_uid")
})
public class AuditLog extends BaseEntity {

    /** BaseEntity only carries a date, and an audit trail is useless without the time. */
    @Column(name = "occurred_at")
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "branch_uid", length = 255)
    private String branchUid;

    /** Readable where the path is recognised, the bare path otherwise. */
    @Column(name = "action", length = 255)
    private String action;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "path", length = 500)
    private String path;

    @Column(name = "query_string", length = 2000)
    private String queryString;

    /** What was sent, with anything credential-shaped masked out. */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status_code")
    private Integer statusCode;

    /** SUCCESS or FAILED - a rejected attempt is worth keeping too. */
    @Column(name = "outcome", length = 20)
    private String outcome;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;
}

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
 * One thing that went wrong, from either side of the app. Written on the way
 * out of a failed request (or posted by the browser) purely so it can be read
 * back in Settings - nothing else in the system reads these rows.
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "error_logs", indexes = {
        // The list is always "newest first", optionally narrowed by source or
        // level, so those are the columns worth indexing.
        @Index(name = "idx_error_logs_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_error_logs_source", columnList = "source"),
        @Index(name = "idx_error_logs_level", columnList = "level")
})
public class ErrorLog extends BaseEntity {

    /** BACKEND or FRONTEND. */
    @Column(name = "source", length = 20)
    private String source;

    /** ERROR for anything unhandled, WARN for a rejected business rule. */
    @Column(name = "level", length = 20)
    private String level;

    /** BaseEntity only carries a date, and an error list is useless without the time. */
    @Column(name = "occurred_at")
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Column(name = "message", length = 2000)
    private String message;

    @Column(name = "exception_type", length = 255)
    private String exceptionType;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    /** Request path for a backend error, router URL for a frontend one. */
    @Column(name = "path", length = 500)
    private String path;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /** Whoever was logged in when it happened, if anyone. */
    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "branch_uid", length = 255)
    private String branchUid;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * What was sent with the failing request - the request body for the
     * backend, or the posted payload for a frontend report. Passwords and
     * tokens are masked before it ever reaches here.
     */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    /** Query string, kept apart from the path so both stay readable. */
    @Column(name = "query_string", length = 2000)
    private String queryString;
}

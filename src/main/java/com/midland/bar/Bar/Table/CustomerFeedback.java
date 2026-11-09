package com.midland.bar.Bar.Table;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * What a customer wrote from the table's window (owner, 2026-11-09): praise,
 * a complaint, a question or advice, with optional stars and contact. Seen by
 * the branch's CEO and manager only.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customer_feedback", indexes = {
        @Index(name = "idx_customer_feedback_branch", columnList = "branch_uid, sent_at")
})
public class CustomerFeedback extends TenantEntity {

    public static final java.util.Set<String> KINDS = java.util.Set.of("PRAISE", "COMPLAINT", "QUESTION", "ADVICE");

    @Column(name = "table_uid", length = 64)
    private String tableUid;

    @Column(name = "table_name", length = 40)
    private String tableName;

    /** PRAISE, COMPLAINT, QUESTION or ADVICE. */
    @Column(name = "kind", length = 12, nullable = false)
    private String kind;

    @Column(name = "message", length = 1000, nullable = false)
    private String message;

    /** 1..5 stars for the service, or none. */
    @Column(name = "rating")
    private Integer rating;

    @Column(name = "customer_name", length = 60)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    /** The waiter serving that table when it was written (from the table's accepted orders), if known. */
    @Column(name = "staff_name", length = 120)
    private String staffName;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "read_by", length = 120)
    private String readBy;

    /**
     * Written by a customer's phone - nobody is signed in, so the branch is
     * the table's (set before saving), not the login's as TenantEntity does.
     */
    @Override
    @PrePersist
    public void prePersist() {
        if (getBranchUid() == null)
            super.prePersist();
    }
}

package com.midland.bar.Support.Model;

import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Something a branch wants to say to the people running the platform - a
 * comment, a question, or a complaint.
 *
 * A branch sees only POS. Without this the only way to raise anything is a
 * phone call, which leaves no record of what was asked, when, or whether
 * anybody answered.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "branch_messages", indexes = {
        // The branch's own list, and the admin list filtered by status -
        // the only two ways this table is ever read.
        @Index(name = "idx_branch_messages_branch", columnList = "branch_uid"),
        @Index(name = "idx_branch_messages_status", columnList = "status, last_activity_at")
})
public class BranchMessage extends BaseEntity {

    public static final String COMMENT = "COMMENT";
    public static final String QUESTION = "QUESTION";
    public static final String COMPLAINT = "COMPLAINT";

    public static final String NEW = "NEW";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String CLOSED = "CLOSED";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_uid")
    private Branch branch;

    /** Kept alongside the reference: a branch can be renamed or removed, and
     *  a complaint should still say who raised it. */
    @Column(name = "branch_name", length = 150)
    private String branchName;

    @Column(name = "category", length = 20)
    private String category;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "body", length = 4000)
    private String body;

    @Column(name = "status", length = 20)
    private String status = NEW;

    @Column(name = "raised_by_uid")
    private String raisedByUid;

    @Column(name = "raised_by_name", length = 150)
    private String raisedByName;

    /**
     * When anything last happened on this thread. Sorting on createdAt would
     * bury a month-old complaint that was answered this morning.
     */
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    /** Whether the last word was the branch's, so an admin list can show
     *  what is actually waiting on them. */
    @Column(name = "awaiting_reply")
    private Boolean awaitingReply = true;
}

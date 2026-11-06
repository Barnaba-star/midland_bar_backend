package com.midland.bar.Bar.JackPoint;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A staff member reached a Jack Point. Found by the system (QUALIFIED); the
 * CEO, manager or accountant then approves it - choosing when the paid days
 * off start, and the bonus goes onto the staff member's commission - or
 * turns it down with a reason. The weeks it covers never count again for
 * the same Jack Point.
 */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "jack_point_awards", indexes = {
        @Index(name = "idx_jack_award_branch", columnList = "branch_uid, status"),
        @Index(name = "idx_jack_award_point_staff", columnList = "jack_point_uid, staff_uid")
})
public class JackPointAward extends TenantEntity {

    public static final String QUALIFIED = "QUALIFIED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "jack_point_uid", nullable = false)
    private String jackPointUid;

    @Column(name = "jack_point_name")
    private String jackPointName;

    @Column(name = "staff_uid", nullable = false)
    private String staffUid;

    @Column(name = "staff_name")
    private String staffName;

    /** First day of the run's first round and of its last round (a Monday for weekly games). */
    @Column(name = "week_from", nullable = false)
    private LocalDate weekFrom;

    @Column(name = "week_to", nullable = false)
    private LocalDate weekTo;

    /** The game's round when won: DAY, WEEK or MONTH (null = WEEK). */
    @Column(name = "period", length = 10)
    private String period;

    /** Last day of the run (for the screens). */
    public LocalDate getRunEnd() {
        return weekTo == null ? null : JackPeriod.end(weekTo, period);
    }

    /** Commission earned over the run's weeks. */
    @Column(name = "commission_total")
    private Integer commissionTotal;

    @Column(name = "status", nullable = false)
    private String status = QUALIFIED;

    /** The reward, copied from the Jack Point when won. */
    @Column(name = "leave_days")
    private Integer leaveDays;

    @Column(name = "bonus_amount")
    private Integer bonusAmount;

    @Column(name = "reward_note", length = 200)
    private String rewardNote;

    /** When the bonus went onto the staff member's commission (null = not yet). */
    @Column(name = "bonus_paid_at")
    private LocalDateTime bonusPaidAt;

    /** The first paid day off, chosen at approval. */
    @Column(name = "leave_from")
    private LocalDate leaveFrom;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_by_name")
    private String decidedByName;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "qualified_at")
    private LocalDateTime qualifiedAt;
}

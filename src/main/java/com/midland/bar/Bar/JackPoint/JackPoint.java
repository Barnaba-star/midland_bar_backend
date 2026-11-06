package com.midland.bar.Bar.JackPoint;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A Jack Point: a game for the waiters set by the CEO, manager or accountant.
 * A staff member whose commission reaches [weeklyTarget] every round (a day,
 * a week or a month - [period]) for [weeks] rounds running wins the reward - paid days off, a cash bonus and/or
 * something written (a crate of soda). Weeks are Monday to Sunday and only
 * count once they are over; they are counted from [startDate].
 */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "jack_points", indexes = {
        @Index(name = "idx_jack_point_branch", columnList = "branch_uid")
})
public class JackPoint extends TenantEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** Commission a staff member has to earn in each week. */
    @Column(name = "weekly_target", nullable = false)
    private Integer weeklyTarget;

    /** How many weeks in a row. */
    @Column(name = "weeks", nullable = false)
    private Integer weeks;

    /** The Monday the first counted week starts. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** DAY, WEEK or MONTH: how long one round is (null = WEEK, the games from before). */
    @Column(name = "period", length = 10)
    private String period = JackPeriod.WEEK;

    /** Paid days off won (0 = none). */
    @Column(name = "leave_days")
    private Integer leaveDays = 0;

    /** Cash bonus won, added to the staff member's commission once approved (0 = none). */
    @Column(name = "bonus_amount")
    private Integer bonusAmount = 0;

    /**
     * The bonus goes straight onto the staff member's commission the moment
     * they qualify (no approval needed for the money; paid days off still wait).
     */
    @Column(name = "auto_bonus")
    private Boolean autoBonus = false;

    /** How the game works, in the boss's words - shown to everyone, staff too. */
    @Column(name = "description", length = 500)
    private String description;

    /** Any other reward, in words. */
    @Column(name = "reward_note", length = 200)
    private String rewardNote;

    /** Switched off: no new winners, nothing counted. Winners already found stay. */
    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "created_by")
    private String createdBy;
}

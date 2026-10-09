package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A staff member handing in less than their bills came to (Mery should bring
 * 100,000 and has 90,000). The 10,000 comes off what she is owed in
 * commission for the day, and off the expected cash of the cashier who took
 * the handover - the bills were paid in full, the money was not.
 */
@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "staff_losses", indexes = {
        @Index(name = "idx_staff_loss_branch_at", columnList = "branch_uid, recorded_at"),
        @Index(name = "idx_staff_loss_recorder", columnList = "recorded_by")
})
public class StaffLoss extends TenantEntity {

    @Column(name = "staff_uid", nullable = false)
    private String staffUid;

    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "staff_code")
    private String staffCode;

    /** What the bills came to. */
    @Column(name = "expected_amount")
    private Integer expectedAmount;

    /** What was handed in. */
    @Column(name = "handed_amount")
    private Integer handedAmount;

    /** The shortage: expected minus handed. */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "note", length = 300)
    private String note;

    /** How the money that fell short was to come in (cash, mpesa, tigopesa...); null was cash. */
    @Column(name = "method", length = 30)
    private String method;

    /** The StaffCommissions row it came off. */
    @Column(name = "commission_uid")
    private String commissionUid;

    /** The login that took the handover - their cash-up expects this much less. */
    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "recorded_by_name")
    private String recordedByName;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}

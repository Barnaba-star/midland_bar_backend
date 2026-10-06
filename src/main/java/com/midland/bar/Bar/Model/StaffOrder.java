package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * What a staff member wrote at Staff Sell, waiting on the supervisor before
 * the drinks leave the counter. Nothing on it touches the bill, the stock or
 * the commission until it is received - then it goes through the same sale
 * as anything else. DRAFT while the staff member is still writing, SENT when
 * they hand over (switch staff, or Send), then RECEIVED or REJECTED.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "staff_orders", indexes = {
        @Index(name = "idx_staff_orders_branch_status", columnList = "branch_uid, status"),
        @Index(name = "idx_staff_orders_bill", columnList = "sales_opened_uid")
})
public class StaffOrder extends TenantEntity {

    public static final String DRAFT = "DRAFT";
    public static final String SENT = "SENT";
    public static final String RECEIVED = "RECEIVED";
    public static final String REJECTED = "REJECTED";

    @Column(name = "sales_opened_uid", nullable = false)
    private String salesOpenedUid;

    @Column(name = "sales_code")
    private String salesCode;

    @Column(name = "staff_uid")
    private String staffUid;

    @Column(name = "staff_code")
    private String staffCode;

    @Column(name = "staff_name")
    private String staffName;

    /** COUNTER (drinks) or CHEF (food) - who receives it; null = any station (older and mixed offline orders). */
    @Column(name = "station", length = 12)
    private String station;

    @Column(name = "status", length = 12, nullable = false)
    private String status = DRAFT;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** The supervisor's login that received or rejected it. */
    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    /**
     * Written at Staff Sell while there was no internet: it went straight onto
     * the bill (no supervisor could see it) and was sent when the line came
     * back. The supervisor looks it over afterwards.
     */
    @Column(name = "offline_received")
    private Boolean offline = false;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdAt ASC")
    private List<StaffOrderLine> lines = new ArrayList<>();

    public int total() {
        return lines.stream().mapToInt(l -> (l.getUnitPrice() == null ? 0 : l.getUnitPrice()) * l.getQuantity()).sum();
    }
}

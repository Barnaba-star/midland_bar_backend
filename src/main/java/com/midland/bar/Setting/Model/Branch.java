package com.midland.bar.Setting.Model;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "branches", indexes = {
        // STAFF branch lists and the commission report both filter on the creator.
        @Index(
                name = "idx_branches_created_by",
                columnList = "created_by"
        ),
        // Looked up on every branch-code check, including at startup.
        @Index(
                name = "idx_branches_branch_code",
                columnList = "branch_code"
        ),
        // The daily expiry job scans on these two together.
        @Index(
                name = "idx_branches_subscription",
                columnList = "subscription_status, close_subscription"
        )
})
public class Branch extends BaseEntity {

    // UID of the user who registered this branch. STAFF only ever sees the
    // branches they created themselves; ROOT and DIRECTOR see them all.
    @Column(name = "created_by")
    private String createdBy;

    @Column(
            name = "branch_name",
            nullable = false,
            length = 150
    )
    private String branchName;

    @Column(
            name = "branch_code",
            nullable = false,
            length = 50
    )
    private String branchCode;


    @Column(
            name = "branch_category",
            length = 100
    )
    private String branchCategory;

    @Column(name = "region")
    private String region;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    /** ACTIVE, or BLOCKED by the main office (nobody in the branch can sign in or work). */
    @Column(name = "status")
    private String status;

    /** Why the main office blocked the branch - shown to its people at sign-in. */
    @Column(name = "blocked_reason", length = 300)
    private String blockedReason;

    @Column(name = "blocked_at")
    private java.time.LocalDateTime blockedAt;

    @Column(name = "blocked_by")
    private String blockedBy;

    public static final String BLOCKED = "BLOCKED";

    /** Blocked by the main office. */
    public boolean isBlocked() {
        return BLOCKED.equalsIgnoreCase(status);
    }

    @Column(
            name = "description",
            length = 500
    )
    private String description;

    @Column(name = "open_subscription")
    private LocalDate openSubscription;

    @Column(name = "close_subscription")
    private LocalDate closeSubscription;

    @Column(name = "subscription_amount")
    private Integer subscriptionAmount;

    @Column(name = "subscription_days")
    private Integer subscriptionDays;

    @Column(name = "subscription_status")
    private String subscriptionStatus;

    @Column(name = "subscription_phone_number")
    private String subscriptionPhoneNumber;

    // Snippe payment reference last applied to this branch's subscription -
    // used to ignore duplicate webhook deliveries (Snippe retries on
    // anything that isn't a fast 2xx, so the same payment.completed event
    // can arrive more than once).
    @Column(name = "last_subscription_payment_ref")
    private String lastSubscriptionPaymentRef;

    /**
     * Why the last payment did not go through, as Snippe reported it. Kept so
     * the login screen can say more than "expired" - "you have no balance" is
     * something the customer can act on.
     */
    @Column(name = "last_payment_failure", length = 500)
    private String lastPaymentFailure;

    /**
     * Snippe's reference for a payment that has been started but not yet
     * confirmed. Kept from the moment it is initiated, not from the moment it
     * completes: a webhook that never arrives leaves a customer who has paid
     * locked out, and without this there is nothing left to ask Snippe about.
     */
    @Column(name = "pending_payment_ref")
    private String pendingPaymentRef;

    @Column(name = "pending_payment_at")
    private java.time.LocalDateTime pendingPaymentAt;
}
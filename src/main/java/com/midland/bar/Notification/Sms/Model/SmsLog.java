package com.midland.bar.Notification.Sms.Model;

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

/** One text the system tried to send, and what became of it. */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "sms_logs", indexes = {
        @Index(name = "idx_sms_logs_sent_at", columnList = "sent_at"),
        @Index(name = "idx_sms_logs_status", columnList = "status")
})
public class SmsLog extends BaseEntity {

    @Column(name = "sent_at")
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    /** Which message this was - USER_CREDENTIALS, and whatever comes later. */
    @Column(name = "template", length = 60)
    private String template;

    /**
     * The text as sent, but only for templates that carry nothing private.
     * A credentials message is recorded as having been sent and no more -
     * putting the password in a second table would undo the point of hashing
     * it in the first.
     */
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    /** SENT, FAILED or SKIPPED. */
    @Column(name = "status", length = 20)
    private String status;

    /** The provider's id for the message, when there is one. */
    @Column(name = "reference", length = 255)
    private String reference;

    @Column(name = "error", length = 500)
    private String error;

    /** Who it was about, when it was about somebody. */
    @Column(name = "recipient_uid", length = 255)
    private String recipientUid;
}

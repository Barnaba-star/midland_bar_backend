package com.midland.bar.Notification.Model;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single notification aimed at one specific user. Broadcasting an
 * event to several people (e.g. everyone in a branch) means inserting
 * one row per recipient rather than one shared row, so each person's
 * read/unread state is independent.
 *
 * titleKey/messageKey are ngx-translate keys, not rendered text, so
 * the notification shows correctly regardless of which language the
 * viewer has selected. paramsJson carries the interpolation values
 * (e.g. sale amount) as a small JSON object.
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_target_user", columnList = "target_user_uid")
})
public class Notification extends BaseEntity {

    @Column(name = "target_user_uid", nullable = false)
    private String targetUserUID;

    @Column(name = "title_key", nullable = false)
    private String titleKey;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(name = "params_json", length = 1000)
    private String paramsJson;

    @Column(name = "icon")
    private String icon;

    @Column(name = "route")
    private String route;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt = LocalDateTime.now();
}

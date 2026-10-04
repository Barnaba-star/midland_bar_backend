package com.midland.bar.Utils.Offline;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An offline operation already applied - its uid is the device's X-Op-Id. A
 * queue resent after a dropped connection finds it here and is not applied twice.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "offline_ops")
public class OfflineOp extends TenantEntity {

    @Column(name = "kind", length = 40)
    private String kind;

    /** The entity it touched (a bill), so a repeat can answer with it. */
    @Column(name = "ref_uid")
    private String refUid;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;
}

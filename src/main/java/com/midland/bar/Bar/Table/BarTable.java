package com.midland.bar.Bar.Table;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A table in a branch (owner, 2026-11-09). Its QR code is a link to the
 * customers' window with this table's token - the token alone tells the
 * system which branch and table the phone is at, so the customer needs no
 * app and no login. A new token makes an old (stolen, copied) card stop working.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "bar_tables", indexes = {
        @Index(name = "idx_bar_tables_branch", columnList = "branch_uid"),
        @Index(name = "idx_bar_tables_token", columnList = "token", unique = true)
})
public class BarTable extends TenantEntity {

    /** 1, 2, 3... - the order cards print in. */
    @Column(name = "number", nullable = false)
    private Integer number;

    /** What the card and the customer see: "Meza 5", "VIP 1". */
    @Column(name = "name", nullable = false, length = 40)
    private String name;

    /** The secret in the QR link - hard to guess, unique across all branches. */
    @Column(name = "token", nullable = false, length = 24)
    private String token;
}

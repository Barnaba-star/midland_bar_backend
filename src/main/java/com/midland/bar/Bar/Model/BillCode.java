package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A name a bill can be opened under - SK-1, SK-2, a table number. Set up in
 * POS Setting; a code is taken while a bill under it is unpaid and free
 * again once that bill is paid.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "bill_codes", indexes = {
        @Index(name = "idx_bill_codes_branch", columnList = "branch_uid")
})
public class BillCode extends TenantEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;
}

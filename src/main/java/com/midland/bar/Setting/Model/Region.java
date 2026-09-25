package com.midland.bar.Setting.Model;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A region a branch can be registered in, and the prefix its branch codes
 * carry - MWANZA gives MWA-001, MWA-002 and so on.
 *
 * These used to be a fixed map in BranchCodeHelper, which meant opening in a
 * new region needed a code change and a deploy.
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "regions", indexes = {
        @Index(name = "idx_regions_name", columnList = "name"),
        @Index(name = "idx_regions_code", columnList = "code")
})
public class Region extends BaseEntity {

    /** Stored upper-cased, since that is how branches are matched against it. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** The branch-code prefix. Short by convention, three letters. */
    @Column(name = "code", nullable = false, length = 10)
    private String code;
}

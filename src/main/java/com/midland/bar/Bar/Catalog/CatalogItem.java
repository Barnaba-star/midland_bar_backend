package com.midland.bar.Bar.Catalog;

import com.midland.bar.Utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A drink the main office describes once for every branch (owner, 2026-11-14):
 * Castle Lite - 24 bottles to a crate, its flavour, its barcodes. A branch
 * ticks the ones it sells and only adds its own selling and buying prices,
 * which differ from branch to branch.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "drink_catalog")
public class CatalogItem extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Ladha: Original, Lemon, Dry... Optional. */
    @Column(name = "flavour", length = 80)
    private String flavour;

    /** Size on the label: 375ml, 500ml, 1L. Optional. */
    @Column(name = "size", length = 40)
    private String size;

    /** DRINK or JUICE - see ProductCategory. */
    @Column(name = "category", length = 20)
    private String category;

    /** What is sold one at a time: Chupa, Kopo. */
    @Column(name = "unit", length = 40)
    private String unit;

    /** What it is bought in: Kreti, Katoni. Null when bought one at a time. */
    @Column(name = "pack_unit", length = 40)
    private String packUnit;

    /** Units in one pack - 24 bottles to a crate. 1 without a pack. */
    @Column(name = "units_per_pack")
    private Integer unitsPerPack;

    @Column(name = "barcode", length = 64)
    private String barcode;

    @Column(name = "pack_barcode", length = 64)
    private String packBarcode;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    /** The name a branch's product gets: "Castle Lite 500ml", "Savanna Dry". */
    public String displayName() {
        StringBuilder b = new StringBuilder(name == null ? "" : name.trim());
        if (flavour != null && !flavour.isBlank())
            b.append(' ').append(flavour.trim());
        if (size != null && !size.isBlank())
            b.append(' ').append(size.trim());
        return b.toString();
    }
}

package com.midland.bar.Bar.Model;

import com.midland.bar.Utils.TenantEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.EnumNaming;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "bar_Staffs", indexes = {
        @Index(
                name = "idx_bar_Staffs_branch",
                columnList = "branch_uid"
        ),
        @Index(
                name = "idx_bar_Staffs_active",
                columnList = "is_active"
        )
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_bar_staffs_branch_code", columnNames = {"branch_uid", "staff_code"})
})
@jakarta.persistence.EntityListeners(com.midland.bar.Config.Security.PrincipalCache.Evict.class)
public class BarStaff extends TenantEntity {
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "middle_name")
    private String middleName;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "phone_number")
    private String phoneNumber;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(name = "description")
    private String description;
    @Column(name = "bar_category")
    private String barCategory;
    @Column(name = "gender")
    private String gender;

    /**
     * Their own picture (owner, 2026-11-12): the wallpaper of their Staff Sell
     * screen, and what a customer sees when calling a waiter from a table's QR.
     * A file name in the uploads folder (staff-<uuid>.jpg).
     */
    @Column(name = "photo", length = 80)
    private String photo;

    /**
     * All their pictures, up to five, comma-separated (owner, 2026-11-12): they
     * take turns as the wallpaper. The first is the one customers see (= photo).
     */
    @Column(name = "photos", length = 600)
    private String photos;

    public java.util.List<String> photoList() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (photos != null)
            for (String p : photos.split(","))
                if (!p.isBlank()) out.add(p.trim());
        if (out.isEmpty() && photo != null && !photo.isBlank())
            out.add(photo);
        return out;
    }

    public void photoList(java.util.List<String> list) {
        photos = list.isEmpty() ? null : String.join(",", list);
        photo = list.isEmpty() ? null : list.get(0);
    }

    /**
     * What the staff member types at Staff Sell to pull up their bills: 001,
     * 002... Given out in order within the branch and never reused, so an old
     * code cannot land a new person's sales on someone who has left.
     */
    @Column(name = "staff_code")
    private String staffCode;

    /**
     * The login this staff member sells under. Sales are rung up by whoever
     * is logged in, and their commission lands on the staff row linked here -
     * created from the user's own details the first time they sell.
     */
    @Column(name = "user_uid")
    private String userUid;

    /**
     * The staff member's own 4-digit PIN for signing in with their code
     * (code = who, PIN = proof). Stored as a bcrypt hash and never sent out:
     * the code is known to the whole bar, the PIN only to them.
     */
    @JsonIgnore
    @ToString.Exclude
    @Column(name = "pin_hash")
    private String pinHash;

    /** Wrong PINs in a row; at 5 the code is locked for a while. */
    @JsonIgnore
    @Column(name = "pin_failed_attempts")
    private Integer pinFailedAttempts;

    /** Code sign-in refused until then, after too many wrong PINs. */
    @JsonIgnore
    @Column(name = "pin_locked_until")
    private java.time.LocalDateTime pinLockedUntil;

    /**
     * Added with a code and PIN the system chose: at their first code
     * sign-in they pick their own code (from free ones offered) and PIN.
     */
    @Column(name = "must_set_code")
    private Boolean mustSetCode;

    /** The PIN the system chose, sent back once when the staff member is added - never stored. */
    @Transient
    private String issuedPin;

    /** For the staff list: whether they can sign in with their code yet. */
    @JsonProperty("hasPin")
    public boolean hasPin() {
        return pinHash != null && !pinHash.isBlank();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_open")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private StoreOpen storeOpen;
}

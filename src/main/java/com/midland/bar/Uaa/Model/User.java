package com.midland.bar.Uaa.Model;

import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Utils.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "users", indexes = {
        // Every branch-scoped user lookup (sale notifications, staff
        // lists, online users, ...) filters on this column. With
        // ~1000 branches sharing the table, an unindexed lookup here
        // would mean a full scan of the whole users table every time.
        @Index(name = "idx_users_branch_uid", columnList = "branch_uid"),
        // Looked up once per authenticated request to build the principal,
        // so this is the single hottest lookup in the system.
        @Index(name = "idx_users_username", columnList = "username")
}, uniqueConstraints = {
        // Login finds a person by this name, so two rows sharing one is not a
        // duplicate record - it is a lookup that cannot answer, and neither
        // person can sign in. UserService picks a free name, but only the
        // database can promise it across two registrations at once.
        @jakarta.persistence.UniqueConstraint(name = "uk_users_username", columnNames = "username")
})
@jakarta.persistence.EntityListeners(com.midland.bar.Config.Security.PrincipalCache.Evict.class)
public class User extends BaseEntity {

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    @JsonIgnore
    private String password;

    @Column(name = "is_root")
    private Boolean isRoot = false;

    @Column(name = "is_blocked")
    private Boolean isBlocked = false;

    /**
     * Set when the account is created, cleared the moment the person chooses
     * their own password. Until then what is stored is the one-time code that
     * travelled to them over SMS - readable by anyone who saw that message,
     * and by whatever carried it. So while this is true the token they hold
     * opens exactly one door: setting a password.
     *
     * Defaults to false, which is what every account that already existed
     * before this was added reads as - they are not dragged through a change
     * they never needed.
     */
    @Column(name = "must_change_password")
    private Boolean mustChangePassword = false;

    /**
     * When the code above stops working. Null on an account that has already
     * set its own password - a password does not expire, only the code does.
     */
    @Column(name = "activation_expires_at")
    private LocalDateTime activationExpiresAt;

    /**
     * Wrong codes entered since the last one was issued. Six digits is a
     * million guesses, which is nothing without this.
     */
    @Column(name = "activation_attempts")
    private Integer activationAttempts = 0;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_uid"),
            inverseJoinColumns = @JoinColumn(name = "role_uid")
    )
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private List<Role> roles;




    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "gender")
    private String gender;

    @Column(name = "email")
    private String email;

    /** The user's home branch - where they were created. getBranch() may answer with another; see activeBranch. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_uid")
    private Branch branch;

    /**
     * Other branches this user may work in, given by the main office. A user
     * with any is asked at login which branch to work in (UserController).
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_branches",
            joinColumns = @JoinColumn(name = "user_uid"),
            inverseJoinColumns = @JoinColumn(name = "branch_uid")
    )
    @JsonIgnore
    private java.util.Set<Branch> extraBranches = new java.util.HashSet<>();

    /**
     * The branch this session works in, chosen at login and carried in the
     * token (JwtAuthenticationFilter sets it). Never stored: two devices can
     * work in two branches at once.
     */
    @Transient
    @JsonIgnore
    private Branch activeBranch;

    /**
     * The branch everything this session reads and writes belongs to - the
     * one chosen at login, else home. Persistence reads the field, not this,
     * so saving the user never moves their home branch.
     */
    public Branch getBranch() {
        return activeBranch != null ? activeBranch : branch;
    }

    /** Where the user was created, whichever branch they are working in now. */
    @JsonIgnore
    public Branch getHomeBranch() {
        return branch;
    }

    /** Home first, then the others - only branches still in use. */
    @JsonIgnore
    public List<Branch> getWorkBranches() {
        List<Branch> out = new java.util.ArrayList<>();
        if (branch != null)
            out.add(branch);
        if (extraBranches != null)
            extraBranches.stream()
                    .filter(b -> b != null && !Boolean.FALSE.equals(b.getIsActive()))
                    .filter(b -> branch == null || !b.getUid().equals(branch.getUid()))
                    .sorted(java.util.Comparator.comparing(b -> b.getBranchName() == null ? "" : b.getBranchName()))
                    .forEach(out::add);
        return out;
    }

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "profile_image")
    private String profileImage;

    /**
     * Where this person's share of the month is sent. Read off the payroll
     * by whoever takes it to the bank, so it is theirs to set - an account
     * number typed in on somebody's behalf is a dispute waiting to happen.
     */
    @Column(name = "account_number", length = 50)
    private String accountNumber;

    /**
     * Which bank that account is at. An account number alone cannot be paid
     * to when people bank in different places, and most do.
     */
    @Column(name = "bank_name", length = 100)
    private String bankName;

    /**
     * The name the account is held in. Not the same thing as the name above:
     * a bank checks what it has on file, and "Grace Ally" on the payroll
     * against "G. A. Mwakalinga" at the counter is a rejected transfer.
     */
    @Column(name = "account_name", length = 150)
    private String accountName;


}

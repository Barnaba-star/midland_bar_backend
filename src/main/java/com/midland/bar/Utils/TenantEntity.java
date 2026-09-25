package com.midland.bar.Utils;

import com.midland.bar.Config.Security.LoggerUser;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.util.Optional;

@MappedSuperclass
@ToString
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Where(clause = "is_active = true")
public class TenantEntity extends SupperEntity{
    @Column(name = "created_at")
    private LocalDate createdAt=LocalDate.now();

    @Column(name = "deleted_at")
    private LocalDate deletedAt;

    @Column(name = "updated_at")
    private LocalDate updatedAt;

    @Column(name = "is_active")
    private Boolean isActive=true;

    @Column(name = "branch_uid")
    private String branchUid;
    public void delete(){
        this.deletedAt = LocalDate.now();
        this.isActive=false;
    }
    public void update(){
        this.updatedAt = LocalDate.now();
    }

    @PrePersist
    public void prePersist() {
        this.branchUid = Optional.ofNullable(LoggerUser.getBranchUID())
                .orElse("MAIN_OFFICE");
    }
}

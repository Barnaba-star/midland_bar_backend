package com.midland.bar.Setting.Model;

import com.midland.bar.Uaa.Model.Permission;
import com.midland.bar.Utils.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "roles")
@jakarta.persistence.EntityListeners(com.midland.bar.Config.Security.PrincipalCache.Evict.class)
public class Role extends BaseEntity {

    @Column(name = "name")
    private String name;

    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "status")
    private String status;

    @Column(name = "category")
    private String category;

    // Batched so loading a user's roles costs one extra select for all of them
    // together, not one per role.
    @ManyToMany(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @JoinTable(
            name = "role_permission",
            joinColumns=@JoinColumn(name = "role_uid"),
            inverseJoinColumns = @JoinColumn(name = "permission_uid")
    )
    private List<Permission> permission;

}

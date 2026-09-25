package com.midland.bar.Utils;
import jakarta.persistence.*;
import lombok.*;

@MappedSuperclass
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class SupperEntity {
    @Id
    @Column(name = "uid", nullable = false, unique = true)
    private String uid = java.util.UUID.randomUUID().toString();
}

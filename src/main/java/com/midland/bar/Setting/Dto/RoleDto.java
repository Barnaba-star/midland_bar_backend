package com.midland.bar.Setting.Dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleDto {
    private String uid;
    @NotBlank(message = "Role Name is required")
    private String name;
    @NotBlank(message = "Role Code is required")
    private String code;
    @NotBlank(message = "Role Category is required")
    private String category;
    @NotBlank(message = "Role Description is required")
    private String description;
    @NotBlank(message = "Role Status is required")
    private String status;
}

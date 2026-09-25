package com.midland.bar.Setting.Dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
@Getter
@Setter
public class UserSettingDTo {
    private String uid;
    @NotNull(message = "Provide Role")
    private List<String> roleUIDS;

    @NotNull(message = "Provide Root status")
    private Boolean isRoot;

    @NotNull(message = "Provide TenantID")
    private String tenantId;
    @NotNull(message = "Provide First Name")
    private String firstName;

    @NotNull(message = "Provide Middle Name")
    private String middleName;

    @NotNull(message = "Provide Last Name")
    private String lastName;

    @NotNull(message = "Provide Date Of birth")
    private Date dateOfBirth;

    @NotNull(message = "Provide Address")
    private String address;

    @NotNull(message = "Provide Phone Number")
    private Number phone;

    @NotNull(message = "Provide Gender")
    private String gender;

}

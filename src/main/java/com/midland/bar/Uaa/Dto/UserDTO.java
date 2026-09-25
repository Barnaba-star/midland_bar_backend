package com.midland.bar.Uaa.Dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
public class UserDTO {
    private String uid;
    @NotNull(message = "Provide First Name")
    private String firstName;
    @NotNull(message = "Provide Middle Name")
    private String middleName;
    @NotNull(message = "Provide Last Name")
    private String lastName;
    @NotNull(message = "Provide Email")
    private String email;
    @NotNull(message = "Provide Gender")
    private String gender;
    @NotNull(message = "Provide Address")
    private String address;
    @NotNull(message = "Provide Role")
    private String role;
    @NotNull(message = "Provide Branch")
    private String branch;
    @NotNull(message = "Provide Date of Birth")
    private LocalDate dob;
    @NotNull(message = "Provide Phone")
    private String phone;
    @NotNull(message = "Provide Root Status")
    private Boolean isRoot;

    /** Optional: most people add these themselves later, from their profile. */
    private String accountNumber;
    private String bankName;
    private String accountName;
}

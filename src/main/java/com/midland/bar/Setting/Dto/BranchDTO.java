package com.midland.bar.Setting.Dto;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

@Getter
@Setter
public class BranchDTO {
    private String uid;
    @NotBlank(message = "Branch Name is required")
    private String branchName;
    @NotBlank(message = "Branch Code is required")
    private String branchCode;
    @NotBlank(message = "Branch Type is required")
    private String branchType;
    @NotBlank(message = "Branch Category is required")
    private String branchCategory;
    @NotBlank(message = "Region is required")
    private String region;
    @NotBlank(message = "Address is required")
    private String address;
    @NotBlank(message = "Phone Number is required")
    private String phone;
    @NotBlank(message = "Status is required")
    private String status;
    @NotBlank(message = "Description is required")
    private String description;
    private LocalDate openSubscription;

    private LocalDate closeSubscription;

    private Integer subscriptionAmount;

    private Integer subscriptionDays;

    private String subscriptionStatus;

    private String subscriptionPhoneNumber;
}

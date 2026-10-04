package com.midland.bar.Bar.Dto;

import com.midland.bar.Bar.Projection.BarProjection;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * A row of Manage Staff: the staff member plus the roles of the login they
 * have, if any. Empty roles means a plain staff member (shown as "Staff").
 * Someone with a role can sign in again, so they cannot be removed until the
 * CEO takes the role away.
 */
@Data
public class StaffRowDTO {
    private String uid;
    private String staffCode;
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private String description;
    private String gender;
    private String barCategory;
    private Boolean active;
    private List<String> roles;
    /** Whether they can sign in with their code yet (a PIN is set). */
    private Boolean hasPin;

    public static StaffRowDTO of(BarProjection staff, List<String> roles) {
        StaffRowDTO row = new StaffRowDTO();
        row.setUid(staff.getUid());
        row.setStaffCode(staff.getStaffCode());
        row.setFirstName(staff.getFirstName());
        row.setMiddleName(staff.getMiddleName());
        row.setLastName(staff.getLastName());
        row.setDateOfBirth(staff.getDateOfBirth());
        row.setPhoneNumber(staff.getPhoneNumber());
        row.setDescription(staff.getDescription());
        row.setGender(staff.getGender());
        row.setBarCategory(staff.getBarCategory());
        row.setActive(staff.getActive());
        row.setRoles(roles);
        row.setHasPin(staff.getHasPin());
        return row;
    }
}

package com.midland.bar.Bar.Dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** First code sign-in: the code and PIN the system gave, and the staff member's own. */
@Data
@EqualsAndHashCode(callSuper = true)
public class StaffSetupDTO extends StaffLoginDTO {
    /** Their own code: 3 digits nobody in the branch holds; empty keeps the current one. */
    private String newCode;
    private String newPin;
}

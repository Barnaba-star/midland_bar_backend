package com.midland.bar.Uaa.Dto;

import lombok.Data;

/** Login page "Umesahau password?": the account, its new password, and a senior login approving. */
@Data
public class ForgotPasswordDTO {
    private String username;
    private String newPassword;
    private String confirmPassword;
    private String approverUsername;
    private String approverPassword;
}

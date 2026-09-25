package com.midland.bar.Setting.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Paying from the login screen, where there is no token yet.
 *
 * The credentials are here because the caller has none: they are checked
 * again exactly as a login would, so this is no more open than /login
 * itself.
 */
@Getter
@Setter
@NoArgsConstructor
public class ExpiredSubscriptionPaymentDTO {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private String mobileNetwork;

    @NotBlank
    private String phoneNumber;

    @NotNull
    private Integer months;
}

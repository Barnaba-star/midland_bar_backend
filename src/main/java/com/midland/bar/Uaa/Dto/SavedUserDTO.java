package com.midland.bar.Uaa.Dto;

import com.midland.bar.Uaa.Model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What comes back from creating or editing a user.
 *
 * The activation code is here because the person being registered is usually
 * standing at the counter: whoever entered their details can read it out and
 * they can start work immediately, instead of everyone waiting on a text.
 * It is returned once, to the one caller who just created the account and
 * could re-issue it anyway - it is never stored in plain and never read back.
 *
 * Null when an existing user is edited; editing touches no credentials.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SavedUserDTO {

    private User user;

    private String activationCode;

    /** How long that code lasts, so the screen can say it without guessing. */
    private Integer validHours;
}

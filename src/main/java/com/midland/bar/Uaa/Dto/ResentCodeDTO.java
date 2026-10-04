package com.midland.bar.Uaa.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What comes back from re-issuing an activation code.
 *
 * Like SavedUserDTO, the code itself is returned to the admin who asked for
 * it, so it can be shown on screen and read out at the counter - the text is
 * a second way for it to arrive, not the only one.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResentCodeDTO {

    /** SENT (also texted), SHOWN (no phone on file - on screen only) or ALREADY_ACTIVATED. */
    private String outcome;

    private String username;

    /** Null when ALREADY_ACTIVATED. */
    private String activationCode;

    private Integer validHours;
}

package com.midland.bar.Uaa.Support;

import java.security.SecureRandom;

/**
 * The one-time code a new account signs in with.
 *
 * It replaces the first password rather than adding to it: a password sent
 * by SMS stays valid forever, so a message read months later still opens the
 * account. A code stops working - once it is used the account has a password
 * of its owner's choosing, and once it is old it is refused outright.
 *
 * Six digits, because it is read off a phone screen and typed on a phone
 * keypad. Six digits is only safe alongside the attempt limit in
 * UserController.login - a million guesses is nothing to a machine, and five
 * is nothing to a person who was actually sent the code.
 */
public final class ActivationCode {

    /** How long a freshly issued code stays usable. */
    public static final int VALID_HOURS = 72;

    /** Wrong tries before the code is burned and has to be re-sent. */
    public static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ActivationCode() {
    }

    public static String generate() {
        // 100000..999999 - never a leading zero, which phones and humans
        // both drop, and which would make the code five digits in practice.
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }
}

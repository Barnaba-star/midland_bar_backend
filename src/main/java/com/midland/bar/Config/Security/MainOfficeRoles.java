package com.midland.bar.Config.Security;

import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Uaa.Model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Who in the main office sees what in Settings (Users, Role, Branch, Commission).
 *
 *  - ROOT and DIRECTOR: every role, user and branch.
 *  - ADMIN (owner's choice, 2026-10-13): like DIRECTOR across every branch, but
 *    never hands out or touches the big roles - ROOT, DIRECTOR, ADMIN.
 *  - Everyone else (STAFF): the branch roles only, their own branches.
 */
public final class MainOfficeRoles {

    /** What a branch works with - all anyone below DIRECTOR ever sees or hands out. */
    public static final List<String> BRANCH_ROLE_CODES =
            List.of("CEO", "MANAGER", "COUNTER", "CHEF", "CASHIER", "STORE_KEEPER", "SUPERVISOR");

    /** Out of ADMIN's reach: whoever holds one of these is left as they are. */
    private static final Set<String> BIG_ROLE_CODES = Set.of("ROOT", "DIRECTOR", "ADMIN");

    private MainOfficeRoles() {
    }

    /** ROOT and DIRECTOR: every role, including the main office's. */
    public static boolean seesAllRoles(User user) {
        return Boolean.TRUE.equals(user.getIsRoot()) || has(user, "ROOT") || has(user, "DIRECTOR");
    }

    /** ADMIN without ROOT/DIRECTOR - the one limited to the roles below its own. */
    public static boolean isAdmin(User user) {
        return has(user, "ADMIN") && !seesAllRoles(user);
    }

    /** ROOT, DIRECTOR and ADMIN see every branch (and every staff member's commission). */
    public static boolean seesAllBranches(User user) {
        return seesAllRoles(user) || has(user, "ADMIN");
    }

    /** The roles this login may see and hand out; null means every role. */
    public static List<String> visibleRoleCodes(User user) {
        if (seesAllRoles(user))
            return null;
        if (!isAdmin(user))
            return BRANCH_ROLE_CODES;
        List<String> codes = new ArrayList<>(BRANCH_ROLE_CODES);
        codes.add("STAFF");
        return codes;
    }

    public static boolean canSee(User user, Role role) {
        List<String> codes = visibleRoleCodes(user);
        return codes == null || (role != null && codes.contains(role.getCode()));
    }

    /** ADMIN may not edit, block or delete someone who holds ROOT, DIRECTOR or ADMIN. */
    public static boolean outOfReach(User caller, User target) {
        if (!isAdmin(caller) || target == null)
            return false;
        if (Boolean.TRUE.equals(target.getIsRoot()))
            return true;
        return target.getRoles() != null
                && target.getRoles().stream().map(Role::getCode).anyMatch(BIG_ROLE_CODES::contains);
    }

    private static boolean has(User user, String code) {
        return user != null && user.getRoles() != null
                && user.getRoles().stream().map(Role::getCode).anyMatch(code::equals);
    }
}

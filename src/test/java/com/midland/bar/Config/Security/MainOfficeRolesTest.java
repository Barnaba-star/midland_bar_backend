package com.midland.bar.Config.Security;

import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Uaa.Model.User;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainOfficeRolesTest {

    private static Role role(String code) {
        Role r = new Role();
        r.setCode(code);
        return r;
    }

    private static User user(String... codes) {
        User u = new User();
        u.setRoles(Arrays.stream(codes).map(MainOfficeRolesTest::role).toList());
        return u;
    }

    @Test
    void adminSeesEveryBranchButOnlyTheRolesBelowItsOwn() {
        User admin = user("ADMIN");
        assertTrue(MainOfficeRoles.seesAllBranches(admin));
        assertFalse(MainOfficeRoles.seesAllRoles(admin));
        List<String> codes = MainOfficeRoles.visibleRoleCodes(admin);
        assertTrue(codes.contains("CEO") && codes.contains("STAFF"));
        assertFalse(codes.contains("ADMIN") || codes.contains("DIRECTOR") || codes.contains("ROOT"));
        assertFalse(MainOfficeRoles.canSee(admin, role("DIRECTOR")));
    }

    @Test
    void adminCannotTouchSomeoneHoldingABigRole() {
        User admin = user("ADMIN");
        assertTrue(MainOfficeRoles.outOfReach(admin, user("DIRECTOR")));
        assertTrue(MainOfficeRoles.outOfReach(admin, user("ADMIN")));
        User root = user();
        root.setIsRoot(true);
        assertTrue(MainOfficeRoles.outOfReach(admin, root));
        assertFalse(MainOfficeRoles.outOfReach(admin, user("CASHIER")));
        assertFalse(MainOfficeRoles.outOfReach(admin, user("STAFF")));
    }

    @Test
    void directorAndStaffAreAsBefore() {
        assertNull(MainOfficeRoles.visibleRoleCodes(user("DIRECTOR")));
        assertFalse(MainOfficeRoles.outOfReach(user("DIRECTOR"), user("ADMIN")));
        assertFalse(MainOfficeRoles.seesAllBranches(user("STAFF")));
        assertFalse(MainOfficeRoles.visibleRoleCodes(user("STAFF")).contains("STAFF"));
    }
}

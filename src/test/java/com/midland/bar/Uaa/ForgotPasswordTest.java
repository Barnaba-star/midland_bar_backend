package com.midland.bar.Uaa;

import com.midland.bar.Uaa.Dto.ForgotPasswordDTO;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Uaa.Service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Login page "Umesahau password?" with a senior login approving - local DB (sample users), rolled back. */
@SpringBootTest
@org.junit.jupiter.api.Tag("local")
@Transactional
class ForgotPasswordTest {

    private static final String MANAGER = "METHEW@2026";   // MANAGER, main branch
    private static final String CASHIER = "JAMES@2026";    // CASHIER, same branch
    private static final String CEO = "MERY@2026";         // CEO, same branch
    private static final String OTHER_CEO = "Amani@2026";  // CEO of another branch
    private static final String PASS = "approver-pass";

    @Autowired PasswordResetService service;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired com.midland.bar.Config.Security.LoginThrottle loginThrottle;

    @BeforeEach
    void setUp() {
        for (String u : new String[]{MANAGER, CEO, OTHER_CEO}) {
            User user = userRepository.findByUsernameForAuthentication(u);
            assertNotNull(user, u + " is in the local sample data");
            user.setPassword(passwordEncoder.encode(PASS));
            userRepository.save(user);
            loginThrottle.succeeded(u);
        }
    }

    private String reset(String target, String approver, String approverPass) {
        ForgotPasswordDTO d = new ForgotPasswordDTO();
        d.setUsername(target);
        d.setNewPassword("mpya-2026");
        d.setConfirmPassword("mpya-2026");
        d.setApproverUsername(approver);
        d.setApproverPassword(approverPass);
        return service.reset(d, new MockHttpServletRequest());
    }

    @Test
    void managerSetsANewPasswordForTheirCashier() {
        assertNull(reset(CASHIER, MANAGER, PASS));
        assertTrue(passwordEncoder.matches("mpya-2026", userRepository.findByUsernameForAuthentication(CASHIER).getPassword()));
    }

    @Test
    void approvalOutOfReachOrWrongChangesNothing() {
        String before = userRepository.findByUsernameForAuthentication(CASHIER).getPassword();
        assertEquals("NOT_APPROVED", reset(CASHIER, MANAGER, "wrong"));
        assertEquals("NOT_APPROVED", reset(CASHIER, OTHER_CEO, PASS), "another branch's CEO");
        assertEquals("NOT_APPROVED", reset(CEO, MANAGER, PASS), "a manager cannot reset the CEO");
        assertEquals("NOT_APPROVED", reset("nobody@2026", MANAGER, PASS));
        assertEquals("APPROVER_SELF", reset(MANAGER, MANAGER, PASS));
        assertEquals(before, userRepository.findByUsernameForAuthentication(CASHIER).getPassword());
        loginThrottle.succeeded(MANAGER);
    }

    @Test
    void ceoResetsTheManager() {
        assertNull(reset(MANAGER, CEO, PASS));
    }
}

package com.midland.bar.Bar;

import com.midland.bar.Bar.Dto.BarStaffDTO;
import com.midland.bar.Bar.Dto.StaffPinResetDTO;
import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Model.StaffCategory;
import com.midland.bar.Bar.Repository.BarStaffRepository;
import com.midland.bar.Bar.Service.BarService;
import com.midland.bar.Bar.Service.StaffSellService;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A staff member who forgot their PIN gets a new one at Staff Sell, a manager approving - local DB, rolled back. */
@SpringBootTest
@org.junit.jupiter.api.Tag("local")
@Transactional
class StaffPinResetTest {

    private static final String LOGIN = "root@root.com";
    private static final String MANAGER_PASSWORD = "manager-test-pass";

    @Autowired UserRepository userRepository;
    @Autowired BarService barService;
    @Autowired StaffSellService staffSellService;
    @Autowired BarStaffRepository barStaffRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;

    private BarStaff staff;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsernameForAuthentication(LOGIN);
        user.setPassword(passwordEncoder.encode(MANAGER_PASSWORD));
        userRepository.save(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));

        BarStaffDTO s = new BarStaffDTO();
        s.setFirstName("Pin");
        s.setLastName("Reset");
        s.setPhoneNumber("0700000002");
        s.setBarCategory(StaffCategory.values()[0].name());
        staff = barService.saveBarStaff(s).getData();
        staff.setPinFailedAttempts(5);
        staff.setPinLockedUntil(LocalDateTime.now().plusMinutes(15));
        barStaffRepository.save(staff);
    }

    private StaffPinResetDTO dto(String pin, String password) {
        StaffPinResetDTO d = new StaffPinResetDTO();
        d.setStaffCode(staff.getStaffCode());
        d.setNewPin(pin);
        d.setUsername(LOGIN);
        d.setPassword(password);
        return d;
    }

    @Test
    void managerApprovalSetsTheNewPinAndLiftsTheLock() {
        assertEquals(Boolean.TRUE, staffSellService.resetPin(dto("4826", MANAGER_PASSWORD)).getData());
        BarStaff after = barStaffRepository.findById(staff.getUid()).orElseThrow();
        assertTrue(passwordEncoder.matches("4826", after.getPinHash()));
        assertEquals(0, after.getPinFailedAttempts());
        assertNull(after.getPinLockedUntil());
    }

    @Test
    void wrongManagerPasswordOrWeakPinChangesNothing() {
        String before = barStaffRepository.findById(staff.getUid()).orElseThrow().getPinHash();
        assertNull(staffSellService.resetPin(dto("4826", "wrong")).getData());
        assertNull(staffSellService.resetPin(dto("1234", MANAGER_PASSWORD)).getData());
        StaffPinResetDTO unknown = dto("4826", MANAGER_PASSWORD);
        unknown.setStaffCode("98765");
        assertNull(staffSellService.resetPin(unknown).getData());
        assertEquals(before, barStaffRepository.findById(staff.getUid()).orElseThrow().getPinHash());
    }

    @Test
    void newCodeFromManageStaffReplacesTheOldPairWithAOneTimeOne() {
        String oldCode = staff.getStaffCode();
        BarStaff issued = barService.resetStaffAccess(staff.getUid()).getData();
        assertNotNull(issued);
        assertNotEquals(oldCode, issued.getStaffCode());
        assertNotNull(issued.getIssuedPin());
        BarStaff after = barStaffRepository.findById(staff.getUid()).orElseThrow();
        assertTrue(after.getMustSetCode(), "they set their own code + PIN at the next sign-in");
        assertTrue(passwordEncoder.matches(issued.getIssuedPin(), after.getPinHash()));
        assertNull(after.getPinLockedUntil());
    }
}

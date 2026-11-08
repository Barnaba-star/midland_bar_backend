package com.midland.bar.Uaa;

import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Sign-in ignores capitals in the username (owner, 2026-11-08) - local DB sample users, rolled back. */
@SpringBootTest
@org.junit.jupiter.api.Tag("local")
@Transactional
class UsernameCaseTest {

    @Autowired UserRepository userRepository;

    @Test
    void anyCaseFindsTheOneAccount() {
        String stored = "Amani@2026";
        User exact = userRepository.findByUsernameForAuthentication(stored);
        assertNotNull(exact, "sample user");
        assertEquals(exact.getUid(), userRepository.findByUsernameForAuthentication("AMANI@2026").getUid());
        assertEquals(exact.getUid(), userRepository.findByUsernameForAuthentication(" amani@2026 ").getUid());
        assertEquals(exact.getUid(), userRepository.findByUsername("aMaNi@2026").getUid());
        assertNull(userRepository.findByUsernameForAuthentication("amani@2027"));
    }

    @Test
    void aNewUsernameMayNotDifferOnlyByCapitals() {
        assertTrue(userRepository.countUsername("amani@2026") > 0, "taken whatever the capitals");
    }
}

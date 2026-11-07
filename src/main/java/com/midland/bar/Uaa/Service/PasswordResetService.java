package com.midland.bar.Uaa.Service;

import com.midland.bar.Config.Security.LoginThrottle;
import com.midland.bar.Setting.Model.Role;
import com.midland.bar.Uaa.Dto.ForgotPasswordDTO;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Umesahau password?" at the login page. There is no SMS or email provider
 * to send a code, so someone senior approves on the spot with their own
 * login: a manager for the branch's people, the CEO for the manager too, the
 * main office for anyone. Wrong approvals count against the approver's login
 * in the same throttle as sign-in, so this is no back door for guessing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    public static final int MIN_PASSWORD_LENGTH = 6;
    private static final Set<String> MAIN_OFFICE = Set.of("ROOT", "DIRECTOR", "ADMIN", "STAFF");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginThrottle loginThrottle;

    /** Null on success, else the error code for the screen (LOCKED:n for minutes). */
    @Transactional
    public String reset(ForgotPasswordDTO dto, HttpServletRequest request) {
        if (dto == null)
            return "MISSING";
        String username = trim(dto.getUsername());
        String approverName = trim(dto.getApproverUsername());
        String pass = dto.getNewPassword() == null ? "" : dto.getNewPassword();
        if (username.isEmpty() || approverName.isEmpty() || dto.getApproverPassword() == null || dto.getApproverPassword().isEmpty())
            return "MISSING";
        if (pass.length() < MIN_PASSWORD_LENGTH)
            return "PASSWORD_SHORT";
        if (!pass.equals(dto.getConfirmPassword()))
            return "PASSWORD_MISMATCH";
        if (username.equalsIgnoreCase(approverName))
            return "APPROVER_SELF";

        long locked = loginThrottle.lockedMinutes(approverName, request);
        if (locked > 0)
            return "LOCKED:" + locked;

        User approver = userRepository.findByUsernameForAuthentication(approverName);
        User target = userRepository.findByUsernameForAuthentication(username);
        boolean approverOk = approver != null
                && passwordEncoder.matches(dto.getApproverPassword(), approver.getPassword())
                && Boolean.TRUE.equals(approver.getIsActive())
                && !Boolean.TRUE.equals(approver.getIsBlocked());
        if (!approverOk) {
            long now = loginThrottle.failed(approverName, request);
            return now > 0 ? "LOCKED:" + now : "NOT_APPROVED";
        }
        loginThrottle.succeeded(approverName);

        // Unknown account and one out of the approver's reach answer alike.
        if (target == null || !Boolean.TRUE.equals(target.getIsActive()) || !mayReset(approver, target))
            return "NOT_APPROVED";
        if (Boolean.TRUE.equals(target.getIsBlocked()))
            return "ACCOUNT_BLOCKED";

        target.setPassword(passwordEncoder.encode(pass));
        target.setMustChangePassword(false);
        target.setActivationExpiresAt(null);
        target.setActivationAttempts(0);
        userRepository.save(target);
        // A fresh start for the account itself: its old wrong guesses are history.
        loginThrottle.succeeded(target.getUsername());
        log.info("Password of {} reset at the login page, approved by {}", target.getUsername(), approver.getUsername());
        return null;
    }

    /**
     * Root resets anyone; ROOT/DIRECTOR anyone but root; ADMIN anyone below
     * ROOT/DIRECTOR/ADMIN. In a branch: the CEO resets its people (not
     * another CEO or the main office), a MANAGER (or STAFF) everyone below
     * CEO and MANAGER - always in a branch both work in.
     */
    static boolean mayReset(User approver, User target) {
        Set<String> a = codes(approver);
        Set<String> t = codes(target);
        boolean approverRoot = Boolean.TRUE.equals(approver.getIsRoot()) || a.contains("ROOT");
        boolean targetRoot = Boolean.TRUE.equals(target.getIsRoot()) || t.contains("ROOT");
        if (targetRoot)
            return approverRoot;
        if (approverRoot || a.contains("DIRECTOR"))
            return true;
        if (a.contains("ADMIN"))
            return !t.contains("DIRECTOR") && !t.contains("ADMIN");

        boolean targetMainOffice = t.stream().anyMatch(MAIN_OFFICE::contains);
        if (targetMainOffice || !shareBranch(approver, target))
            return false;
        if (a.contains("CEO"))
            return !t.contains("CEO");
        if (a.contains("MANAGER") || a.contains("STAFF"))
            return !t.contains("CEO") && !t.contains("MANAGER");
        return false;
    }

    private static boolean shareBranch(User a, User b) {
        Set<String> mine = a.getWorkBranches().stream().map(x -> x.getUid()).filter(Objects::nonNull).collect(Collectors.toSet());
        return b.getWorkBranches().stream().anyMatch(x -> mine.contains(x.getUid()));
    }

    private static Set<String> codes(User u) {
        if (u.getRoles() == null)
            return Set.of();
        return u.getRoles().stream().map(Role::getCode).filter(Objects::nonNull)
                .map(String::toUpperCase).collect(Collectors.toSet());
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}

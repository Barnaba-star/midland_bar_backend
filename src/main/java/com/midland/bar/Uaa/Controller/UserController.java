package com.midland.bar.Uaa.Controller;
import com.midland.bar.Config.Security.JwtTokenUtil;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Uaa.Dto.DataDTO;
import com.midland.bar.Uaa.Dto.LoginDTO;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Support.ActivationCode;
import jakarta.validation.Valid;
import com.midland.bar.Setting.Dto.ExpiredSubscriptionPaymentDTO;
import com.midland.bar.Setting.Service.SettingService;
import com.midland.bar.Setting.Service.PlatformSettingService;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Uaa.Service.UserService;
import com.midland.bar.Utils.Responses.Response;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/authentication")
@RequiredArgsConstructor
public class UserController {
    @Autowired
    private  UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final PlatformSettingService platformSettingService;
    private final SettingService settingService;




    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO loginDTO, HttpServletResponse response) {
        if (loginDTO == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Provide Login Details");
        }

        User user = userRepository.findByUsername(loginDTO.getUsername());
        if (user == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("User Not Found");
        }

        boolean awaitingActivation = Boolean.TRUE.equals(user.getMustChangePassword());

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            // A six-digit code is a million guesses, which is nothing to a
            // machine. Spend them and the code dies - an admin re-sends a new
            // one. A real password has no such counter; wearing one out by
            // guessing would be a way to lock people out of their own accounts.
            if (awaitingActivation) {
                int attempts = user.getActivationAttempts() == null ? 0 : user.getActivationAttempts();
                user.setActivationAttempts(attempts + 1);
                if (attempts + 1 >= ActivationCode.MAX_ATTEMPTS) {
                    user.setActivationExpiresAt(LocalDateTime.now());
                }
                userRepository.save(user);
            }
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid Username or Password");
        }

        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            // A code, not a sentence: "Account Blocked" reached the screen in
            // English and read like a fault rather than a decision somebody
            // made about this person.
            Map<String, Object> blocked = new LinkedHashMap<>();
            blocked.put("status", 403);
            blocked.put("code", "ACCOUNT_BLOCKED");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(blocked);
        }

        // The right code, but too late - or already spent on wrong guesses.
        // Says so plainly, because "wrong password" would send them hunting
        // for a typo in something that was never going to work again.
        if (awaitingActivation
                && user.getActivationExpiresAt() != null
                && user.getActivationExpiresAt().isBefore(LocalDateTime.now())) {
            Map<String, Object> expiredCode = new LinkedHashMap<>();
            expiredCode.put("status", 403);
            expiredCode.put("code", "ACTIVATION_CODE_EXPIRED");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(expiredCode);
        }

        // An account with no roles used to be handed ROOT by the token
        // builder. It no longer is, which leaves it with nothing at all -
        // so say that, rather than letting them in to an empty application.
        boolean hasRole = user.getRoles() != null && !user.getRoles().isEmpty();
        if (!hasRole && !Boolean.TRUE.equals(user.getIsRoot())) {
            Map<String, Object> noRole = new LinkedHashMap<>();
            noRole.put("status", 403);
            noRole.put("code", "NO_ROLE_ASSIGNED");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(noRole);
        }

        // Branch must be within its free/paid period to log in. ROOT users
        // and the platform's own ROOT branch are exempt - they're not a
        // paying customer branch. closeSubscription == null means an admin
        // hasn't configured a plan for this branch yet, which we treat as
        // unrestricted rather than locking brand new branches out before
        // anyone's had a chance to set one up.
        Branch branch = user.getBranch();
        boolean exempt = Boolean.TRUE.equals(user.getIsRoot())
                || (branch != null && "ROOT".equalsIgnoreCase(branch.getBranchCode()));

        // A grace period keeps a branch working for a few days past its end
        // date rather than locking them out the same morning. Zero - the
        // default - is the old behaviour exactly.
        Integer graceDays = platformSettingService.current().getGracePeriodDays();
        LocalDate lockoutDate = LocalDate.now().minusDays(graceDays == null ? 0 : graceDays);

        if (!exempt
                && branch != null
                && branch.getCloseSubscription() != null
                && branch.getCloseSubscription().isBefore(lockoutDate)) {
            // A code and the figures, deliberately no message: the wording a
            // customer reads belongs to the screen, which has it translated.
            // Sending one here would only get shown instead - in English, and
            // phrased like a service layer.
            Map<String, Object> expired = new LinkedHashMap<>();
            expired.put("status", 403);
            expired.put("code", "SUBSCRIPTION_EXPIRED");
            expired.put("branchName", branch.getBranchName());
            expired.put("monthlyAmount", branch.getSubscriptionAmount());
            // Whether a payment was already tried, and how it went. Without
            // this the screen repeats "expired" after a declined payment and
            // says nothing about why paying did not help.
            expired.put("subscriptionStatus", branch.getSubscriptionStatus());
            expired.put("paymentFailure", branch.getLastPaymentFailure());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(expired);
        }

        try {
            if (awaitingActivation) {
                user.setActivationAttempts(0);
            }
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
            String token = jwtTokenUtil.generateToken(user);
            Cookie cookie = new Cookie("jwt_token", token);
            cookie.setHttpOnly(false);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 24);
            cookie.setSecure(false);
            response.addCookie(cookie);
            return ResponseEntity.ok(Map.of("token", token));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error in setting Token");
        }
    }

    /**
     * Returns a fresh token alongside the result. The one the caller is
     * holding still says the password must be changed, so without this they
     * would change it and then be locked out of everything by their own
     * now-stale token until they logged in again.
     */
    @PostMapping("/changePassword")
    public Response<Map<String, Object>> changePassword(@RequestBody DataDTO dataDTO){
        Response<User> result = userService.changePassword(dataDTO);
        if (result.getData() == null) {
            return new Response<>(result.getMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uid", result.getData().getUid());
        body.put("token", jwtTokenUtil.generateToken(result.getData()));
        return new Response<>(body);
    }

    /**
     * The way out of the dead end: a branch whose subscription has lapsed
     * cannot log in, so it cannot reach the normal payment screen either.
     * This sits under /authentication (open by design) and re-checks the
     * credentials itself. It issues no token - paying is all it does.
     */
    @PostMapping("/paySubscription")
    public Response<Branch> paySubscription(@Valid @RequestBody ExpiredSubscriptionPaymentDTO dto) {
        return settingService.payExpiredSubscription(dto);
    }
}

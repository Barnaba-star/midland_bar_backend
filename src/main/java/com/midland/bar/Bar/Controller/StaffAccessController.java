package com.midland.bar.Bar.Controller;

import com.midland.bar.Bar.Dto.StaffLoginDTO;
import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Repository.BarStaffRepository;
import com.midland.bar.Bar.Service.StaffCodeService;
import com.midland.bar.Config.Security.JwtTokenUtil;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Setting.Service.PlatformSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Staff members signing in with their staff code + PIN, straight into Staff Sell.
 *
 * The code says who, the PIN proves it, and the device says which branch:
 * codes are only unique within a branch, so the device must first be
 * registered to one - which happens when a member of that branch signs in on
 * it. Five wrong PINs lock that code for 15 minutes.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class StaffAccessController {

    static final int MAX_PIN_TRIES = 5;
    static final int LOCK_MINUTES = 15;

    private final JwtTokenUtil jwtTokenUtil;
    private final BarStaffRepository barStaffRepository;
    private final BranchRepository branchRepository;
    private final PlatformSettingService platformSettingService;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Ties this device to the signed-in user's branch, for staff code sign-in.
     * Only someone who sells in the branch (SAVE_SALES) can register a device -
     * a view-only visitor from the main office cannot.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('SAVE_SALES')")
    @PostMapping("/bar/device/register")
    public ResponseEntity<Map<String, Object>> registerDevice() {
        String branchUID = LoggerUser.getBranchUID();
        if (branchUID == null)
            return ResponseEntity.badRequest().body(Map.of("code", "NO_BRANCH"));
        Branch branch = branchRepository.findById(branchUID).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceToken", jwtTokenUtil.generateDeviceToken(branchUID, LoggerUser.getEmail()));
        body.put("branchName", branch == null ? null : branch.getBranchName());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/authentication/staffLogin")
    public ResponseEntity<Map<String, Object>> staffLogin(@RequestBody StaffLoginDTO dto) {
        String branchUID = dto == null ? null : jwtTokenUtil.deviceBranch(dto.getDeviceToken());
        if (branchUID == null)
            return refuse(HttpStatus.UNAUTHORIZED, "DEVICE_NOT_REGISTERED", null);
        Branch branch = branchRepository.findById(branchUID).orElse(null);
        if (branch == null)
            return refuse(HttpStatus.UNAUTHORIZED, "DEVICE_NOT_REGISTERED", null);

        // Same lapsed-subscription rule as an ordinary sign-in (ROOT branch exempt).
        Integer graceDays = platformSettingService.current().getGracePeriodDays();
        LocalDate lockoutDate = LocalDate.now().minusDays(graceDays == null ? 0 : graceDays);
        if (!"ROOT".equalsIgnoreCase(branch.getBranchCode())
                && branch.getCloseSubscription() != null
                && branch.getCloseSubscription().isBefore(lockoutDate))
            return refuse(HttpStatus.FORBIDDEN, "SUBSCRIPTION_EXPIRED", null);

        Optional<BarStaff> found = byCode(dto.getStaffCode(), branchUID);
        // One answer for "no such code" and "wrong PIN": the reply must not
        // tell a guesser which codes exist.
        if (found.isEmpty())
            return refuse(HttpStatus.UNAUTHORIZED, "INVALID_STAFF_LOGIN", null);
        BarStaff staff = found.get();

        LocalDateTime now = LocalDateTime.now();
        if (staff.getPinLockedUntil() != null && staff.getPinLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, ChronoUnit.MINUTES.between(now, staff.getPinLockedUntil()) + 1);
            return refuse(HttpStatus.FORBIDDEN, "STAFF_LOCKED", minutes);
        }
        if (!staff.hasPin())
            return refuse(HttpStatus.FORBIDDEN, "NO_PIN_SET", null);

        String pin = dto.getPin() == null ? "" : dto.getPin().trim();
        if (!passwordEncoder.matches(pin, staff.getPinHash())) {
            int tries = (staff.getPinFailedAttempts() == null ? 0 : staff.getPinFailedAttempts()) + 1;
            if (tries >= MAX_PIN_TRIES) {
                staff.setPinFailedAttempts(0);
                staff.setPinLockedUntil(now.plusMinutes(LOCK_MINUTES));
                barStaffRepository.save(staff);
                log.warn("Staff code {} locked in branch {} after {} wrong PINs", staff.getStaffCode(), branchUID, MAX_PIN_TRIES);
                return refuse(HttpStatus.FORBIDDEN, "STAFF_LOCKED", (long) LOCK_MINUTES);
            }
            staff.setPinFailedAttempts(tries);
            barStaffRepository.save(staff);
            return refuse(HttpStatus.UNAUTHORIZED, "INVALID_STAFF_LOGIN", null);
        }

        staff.setPinFailedAttempts(0);
        staff.setPinLockedUntil(null);
        barStaffRepository.save(staff);
        String fullName = Stream.of(staff.getFirstName(), staff.getMiddleName(), staff.getLastName())
                .filter(s -> s != null && !s.isBlank()).map(String::trim).collect(Collectors.joining(" "));
        log.info("Staff {} signed in with their code in branch {}", staff.getStaffCode(), branchUID);
        return ResponseEntity.ok(Map.of("token", jwtTokenUtil.generateStaffToken(staff, branchUID, fullName)));
    }

    private Optional<BarStaff> byCode(String raw, String branchUID) {
        String code = StaffCodeService.normalise(raw);
        if (code.isEmpty())
            return Optional.empty();
        Optional<BarStaff> staff = barStaffRepository.findByStaffCode(code, branchUID);
        if (staff.isEmpty() && code.matches("\\d{1,3}"))
            staff = barStaffRepository.findByStaffCode(String.format("%03d", Integer.parseInt(code)), branchUID);
        return staff;
    }

    private static ResponseEntity<Map<String, Object>> refuse(HttpStatus status, String code, Long minutes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("code", code);
        if (minutes != null)
            body.put("minutes", minutes);
        return ResponseEntity.status(status).body(body);
    }
}

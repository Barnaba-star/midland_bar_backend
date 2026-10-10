package com.midland.bar.Bar.Controller;

import com.midland.bar.Bar.Dto.StaffLoginDTO;
import com.midland.bar.Bar.Dto.StaffSetupDTO;
import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Service.StaffPinRules;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Staff members signing in with their staff code + PIN, straight into Staff Sell,
 * from any device (their own phone included).
 *
 * The code says who and the PIN proves it. Codes are only unique within a
 * branch, so a registered device (one a member of the branch has signed in
 * on) narrows the search to its branch; without one, code + PIN are matched
 * across branches. Five wrong PINs lock that code for 15 minutes.
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
    private final com.midland.bar.Bar.Service.StaffCodeService staffCodeService;
    private final org.springframework.beans.factory.ObjectProvider<com.midland.bar.Bar.Service.BarService> barServiceProvider;

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

    /**
     * The counter sells too: customers sitting at the counter order there.
     * A COUNTER login gets a staff session as itself (its own staff row,
     * made on first use) - the same limits as a waiter's code sign-in: its
     * own bills only, orders, "paid by phone" notes and the handover to the
     * cashier, never taking payment.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('COUNTER_SELL')")
    @PostMapping("/bar/staffSell/mySession")
    public ResponseEntity<Map<String, Object>> mySession() {
        String branchUID = LoggerUser.getBranchUID();
        if (branchUID == null)
            return ResponseEntity.badRequest().body(Map.of("code", "NO_BRANCH"));
        BarStaff staff = barServiceProvider.getObject().myStaff();
        String fullName = Stream.of(staff.getFirstName(), staff.getMiddleName(), staff.getLastName())
                .filter(x -> x != null && !x.isBlank()).map(String::trim).collect(Collectors.joining(" "));
        log.info("{} sells at the counter as staff {}", LoggerUser.getEmail(), staff.getStaffCode());
        return ResponseEntity.ok(Map.of("token", jwtTokenUtil.generateStaffToken(staff, branchUID, fullName)));
    }

    /**
     * Staff code + PIN. The branch comes from the device's ticket when it has
     * one; otherwise the code is looked up in every branch and the PIN picks
     * the person (codes repeat across branches, code + PIN almost never). If
     * it still fits staff in two branches they are asked which (CHOOSE_BRANCH).
     */
    @PostMapping("/authentication/staffLogin")
    public ResponseEntity<Map<String, Object>> staffLogin(@RequestBody StaffLoginDTO dto) {
        Object checked = authenticate(dto);
        if (!(checked instanceof BarStaff staff))
            return asResponse(checked);
        // Added with a code and PIN the system chose: they pick their own
        // first, from free codes offered (or keep the one they have).
        if (Boolean.TRUE.equals(staff.getMustSetCode())) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "SET_CODE");
            body.put("currentCode", staff.getStaffCode());
            body.put("suggestions", staffCodeService.freeCodes(3));
            body.put("branchUID", staff.getBranchUid());
            return ResponseEntity.ok(body);
        }
        return signedIn(staff);
    }

    /**
     * First sign-in of a staff member the system gave a code and PIN: with
     * that code and PIN, they set their own code (4 digits nobody in any
     * branch holds, or keep theirs) and their own PIN, and are signed in.
     */
    @PostMapping("/authentication/staffSetup")
    public ResponseEntity<Map<String, Object>> staffSetup(@RequestBody StaffSetupDTO dto) {
        Object checked = authenticate(dto);
        if (!(checked instanceof BarStaff staff))
            return asResponse(checked);
        if (!Boolean.TRUE.equals(staff.getMustSetCode()))
            return refuse(HttpStatus.BAD_REQUEST, "ALREADY_SET", null);

        String newCode = StaffCodeService.normalise(dto.getNewCode());
        if (newCode.isEmpty())
            newCode = staff.getStaffCode();
        // Their own code: 4 digits nobody in ANY branch holds (or keep the one
        // the system gave).
        boolean keeping = newCode.equals(StaffCodeService.normalise(staff.getStaffCode()));
        if (!keeping && !StaffCodeService.isNewCode(newCode))
            return refuse(HttpStatus.BAD_REQUEST, "CODE_FORMAT", null);
        // The code is secret: say it is taken, never by whom.
        if (!keeping && barStaffRepository.countCodeHoldersAnyBranch(newCode, staff.getUid()) > 0)
            return refuse(HttpStatus.CONFLICT, "CODE_TAKEN", null);

        String newPin = dto.getNewPin() == null ? "" : dto.getNewPin().trim();
        String problem = StaffPinRules.problem(newPin);
        if (problem != null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 400);
            body.put("code", "PIN_RULE");
            body.put("message", problem);
            return ResponseEntity.badRequest().body(body);
        }
        if (passwordEncoder.matches(newPin, staff.getPinHash()))
            return refuse(HttpStatus.BAD_REQUEST, "PIN_SAME", null);

        staff.setStaffCode(newCode);
        staff.setPinHash(passwordEncoder.encode(newPin));
        staff.setMustSetCode(false);
        barStaffRepository.save(staff);
        log.info("Staff {} set their own code and PIN in branch {}", newCode, staff.getBranchUid());
        return signedIn(staff);
    }

    @SuppressWarnings("unchecked")
    private static ResponseEntity<Map<String, Object>> asResponse(Object o) {
        return (ResponseEntity<Map<String, Object>>) o;
    }

    private ResponseEntity<Map<String, Object>> signedIn(BarStaff staff) {
        String branchUID = staff.getBranchUid();
        String fullName = Stream.of(staff.getFirstName(), staff.getMiddleName(), staff.getLastName())
                .filter(x -> x != null && !x.isBlank()).map(String::trim).collect(Collectors.joining(" "));
        log.info("Staff {} signed in with their code in branch {}", staff.getStaffCode(), branchUID);
        return ResponseEntity.ok(Map.of("token", jwtTokenUtil.generateStaffToken(staff, branchUID, fullName)));
    }

    /**
     * Code + PIN checked: the staff member (wrong-PIN count cleared), or the
     * reply to send instead - a refusal, or CHOOSE_BRANCH.
     */
    private Object authenticate(StaffLoginDTO dto) {
        if (dto == null)
            return refuse(HttpStatus.UNAUTHORIZED, "INVALID_STAFF_LOGIN", null);
        String deviceBranch = jwtTokenUtil.deviceBranch(dto.getDeviceToken());
        String chosenBranch = dto.getBranchUID() != null && !dto.getBranchUID().isBlank() ? dto.getBranchUID() : null;

        List<BarStaff> candidates;
        if (chosenBranch != null)
            candidates = byCode(dto.getStaffCode(), chosenBranch).map(List::of).orElse(List.of());
        else if (deviceBranch != null) {
            candidates = byCode(dto.getStaffCode(), deviceBranch).map(List::of).orElse(List.of());
            // The phone was registered in another branch (someone else signed in
            // on it): if this code and PIN are not that branch's, look in every
            // branch rather than turn the staff member away.
            String typed = dto.getPin() == null ? "" : dto.getPin().trim();
            if (candidates.stream().noneMatch(c -> c.hasPin() && passwordEncoder.matches(typed, c.getPinHash())))
                candidates = byCodeAnyBranch(dto.getStaffCode());
        } else
            candidates = byCodeAnyBranch(dto.getStaffCode());
        // One answer for "no such code" and "wrong PIN": the reply must not
        // tell a guesser which codes exist.
        if (candidates.isEmpty())
            return refuse(HttpStatus.UNAUTHORIZED, "INVALID_STAFF_LOGIN", null);

        LocalDateTime now = LocalDateTime.now();
        List<BarStaff> open = candidates.stream()
                .filter(c -> c.getPinLockedUntil() == null || !c.getPinLockedUntil().isAfter(now))
                .toList();
        if (open.isEmpty()) {
            LocalDateTime until = candidates.stream().map(BarStaff::getPinLockedUntil).min(LocalDateTime::compareTo).orElse(now);
            return refuse(HttpStatus.FORBIDDEN, "STAFF_LOCKED", Math.max(1, ChronoUnit.MINUTES.between(now, until) + 1));
        }
        List<BarStaff> withPin = open.stream().filter(BarStaff::hasPin).toList();
        if (withPin.isEmpty())
            return refuse(HttpStatus.FORBIDDEN, "NO_PIN_SET", null);

        String pin = dto.getPin() == null ? "" : dto.getPin().trim();
        List<BarStaff> matched = withPin.stream().filter(c -> passwordEncoder.matches(pin, c.getPinHash())).toList();

        if (matched.isEmpty()) {
            // Every holder of the code takes the wrong guess: a guesser
            // cannot spread tries across branches to dodge the lock.
            boolean lockedNow = false;
            for (BarStaff c : withPin) {
                int tries = (c.getPinFailedAttempts() == null ? 0 : c.getPinFailedAttempts()) + 1;
                if (tries >= MAX_PIN_TRIES) {
                    c.setPinFailedAttempts(0);
                    c.setPinLockedUntil(now.plusMinutes(LOCK_MINUTES));
                    lockedNow = true;
                    log.warn("Staff code {} locked in branch {} after {} wrong PINs", c.getStaffCode(), c.getBranchUid(), MAX_PIN_TRIES);
                } else {
                    c.setPinFailedAttempts(tries);
                }
            }
            barStaffRepository.saveAll(withPin);
            return lockedNow && withPin.size() == 1
                    ? refuse(HttpStatus.FORBIDDEN, "STAFF_LOCKED", (long) LOCK_MINUTES)
                    : refuse(HttpStatus.UNAUTHORIZED, "INVALID_STAFF_LOGIN", null);
        }

        if (matched.size() > 1) {
            // The same code and PIN in two branches: which one are they working in?
            List<Map<String, Object>> branches = new ArrayList<>();
            for (BarStaff c : matched) {
                Branch b = branchRepository.findById(c.getBranchUid()).orElse(null);
                if (b == null)
                    continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("uid", b.getUid());
                row.put("branchName", b.getBranchName());
                row.put("branchCode", b.getBranchCode());
                row.put("home", false);
                row.put("viewOnly", false);
                branches.add(row);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "CHOOSE_BRANCH");
            body.put("branches", branches);
            return ResponseEntity.ok(body);
        }

        BarStaff staff = matched.get(0);
        String branchUID = staff.getBranchUid();
        Branch branch = branchRepository.findById(branchUID).orElse(null);
        if (branch == null)
            return refuse(HttpStatus.UNAUTHORIZED, "INVALID_STAFF_LOGIN", null);

        // Blocked by the main office: no staff code signs in there.
        if (branch.isBlocked())
            return refuse(HttpStatus.FORBIDDEN, "BRANCH_BLOCKED", null);

        // Same lapsed-subscription rule as an ordinary sign-in (ROOT branch exempt).
        Integer graceDays = platformSettingService.current().getGracePeriodDays();
        LocalDate lockoutDate = LocalDate.now().minusDays(graceDays == null ? 0 : graceDays);
        if (!"ROOT".equalsIgnoreCase(branch.getBranchCode())
                && branch.getCloseSubscription() != null
                && branch.getCloseSubscription().isBefore(lockoutDate))
            return refuse(HttpStatus.FORBIDDEN, "SUBSCRIPTION_EXPIRED", null);

        staff.setPinFailedAttempts(0);
        staff.setPinLockedUntil(null);
        return barStaffRepository.save(staff);
    }

    private List<BarStaff> byCodeAnyBranch(String raw) {
        String code = StaffCodeService.normalise(raw);
        if (code.isEmpty())
            return List.of();
        List<BarStaff> staff = barStaffRepository.findAllByStaffCodeAnyBranch(code);
        if (staff.isEmpty() && code.matches("\\d{1,3}"))
            staff = barStaffRepository.findAllByStaffCodeAnyBranch(String.format("%03d", Integer.parseInt(code)));
        return staff;
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

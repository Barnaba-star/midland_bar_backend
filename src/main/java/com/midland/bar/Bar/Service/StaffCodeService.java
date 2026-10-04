package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Repository.BarStaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Staff codes - the number a staff member types at Staff Sell. A new staff
 * member gets a random three-digit code (100-999) that nobody in the branch
 * holds, so the next code can't be guessed from the last. Never reused.
 * Staff from before codes existed get theirs on startup, numbered in the
 * order they joined.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffCodeService implements ApplicationRunner {

    private final BarStaffRepository barStaffRepository;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** How a code is stored and compared: trimmed, upper case. */
    public static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    /** Letters, digits and dashes, 1 to 10 of them - short enough to type, and fit to prefix a bill code. */
    public static boolean isValid(String code) {
        return code.matches("[A-Z0-9-]{1,10}");
    }

    /**
     * A random three-digit code no one in the branch holds (inactive staff
     * included, since codes are never reused). Once all 900 are taken it
     * falls back to the number after the highest.
     */
    public String nextCode(String branchUID) {
        List<String> codes = barStaffRepository.findAllStaffCodes(branchUID);
        Set<String> taken = new HashSet<>();
        for (String code : codes)
            taken.add(normalise(code));
        List<String> free = new ArrayList<>();
        for (int n = 100; n <= 999; n++) {
            String candidate = String.valueOf(n);
            if (!taken.contains(candidate))
                free.add(candidate);
        }
        if (free.isEmpty())
            return format(highest(codes) + 1);
        return free.get(RANDOM.nextInt(free.size()));
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        replaceTypedCodes();
        List<BarStaff> missing = barStaffRepository.findWithoutCode();
        if (missing.isEmpty())
            return;
        Map<String, Integer> lastByBranch = new HashMap<>();
        for (BarStaff staff : missing) {
            int next = lastByBranch.computeIfAbsent(staff.getBranchUid(),
                    branch -> highest(barStaffRepository.findAllStaffCodes(branch))) + 1;
            staff.setStaffCode(format(next));
            lastByBranch.put(staff.getBranchUid(), next);
        }
        barStaffRepository.saveAll(missing);
        log.info("Gave staff codes to {} staff member(s) that had none", missing.size());
    }

    /**
     * Codes typed back when the form had a code field (JAMES, K1) become a
     * random three-digit one like every new staff member's. Numeric codes
     * (001, 245) stay. Someone with an unpaid bill or an undecided Staff Sell
     * order keeps theirs for now - the bill codes were made from it - and is
     * picked up on a later startup once that work is closed.
     */
    private void replaceTypedCodes() {
        int replaced = 0;
        for (BarStaff staff : barStaffRepository.findWithCode()) {
            if (normalise(staff.getStaffCode()).matches("\\d+"))
                continue;
            if (barStaffRepository.countOpenWork(staff.getUid()) > 0) {
                log.info("Staff {} keeps code {} until their open bills and orders are closed", staff.getUid(), staff.getStaffCode());
                continue;
            }
            // Saved one at a time so the next nextCode sees this one as taken.
            staff.setStaffCode(nextCode(staff.getBranchUid()));
            barStaffRepository.saveAndFlush(staff);
            replaced++;
        }
        if (replaced > 0)
            log.info("Gave {} staff member(s) a three-digit code in place of a typed one", replaced);
    }

    private static int highest(List<String> codes) {
        int max = 0;
        for (String code : codes) {
            try {
                max = Math.max(max, Integer.parseInt(code.trim()));
            } catch (NumberFormatException ignored) {
                // Only numeric codes are ever issued; anything else is skipped.
            }
        }
        return max;
    }

    private static String format(int n) {
        return String.format("%03d", n);
    }
}

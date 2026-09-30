package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Repository.BarStaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Staff codes - 001, 002... - the number a staff member types at Staff Sell.
 * Handed out in order within a branch, never reused. Staff from before codes
 * existed get theirs on startup, numbered in the order they joined.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffCodeService implements ApplicationRunner {

    private final BarStaffRepository barStaffRepository;

    /** How a code is stored and compared: trimmed, upper case. */
    public static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    /** Letters, digits and dashes, 1 to 10 of them - short enough to type, and fit to prefix a bill code. */
    public static boolean isValid(String code) {
        return code.matches("[A-Z0-9-]{1,10}");
    }

    /** The code after the highest one the branch has handed out. */
    public String nextCode(String branchUID) {
        return format(highest(barStaffRepository.findStaffCodes(branchUID)) + 1);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<BarStaff> missing = barStaffRepository.findWithoutCode();
        if (missing.isEmpty())
            return;
        Map<String, Integer> lastByBranch = new HashMap<>();
        for (BarStaff staff : missing) {
            int next = lastByBranch.computeIfAbsent(staff.getBranchUid(),
                    branch -> highest(barStaffRepository.findStaffCodes(branch))) + 1;
            staff.setStaffCode(format(next));
            lastByBranch.put(staff.getBranchUid(), next);
        }
        barStaffRepository.saveAll(missing);
        log.info("Gave staff codes to {} staff member(s) that had none", missing.size());
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

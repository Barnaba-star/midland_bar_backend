package com.midland.bar.Bar.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Staff Sell bills used to be numbered from the staff member's code
 * ("4821-1"), which is their secret: anyone who read it off a bill, a report
 * or the Sales page could sell on their behalf. New bills carry the initials
 * (StaffSellService.billPrefix); this renames the old ones the same way, once,
 * with the copies kept on staff orders and voided lines. Idempotent: a renamed
 * bill no longer starts with its code, so each later start finds nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillCodeSecretBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer renamed = tx.execute(status -> rename());
            if (renamed != null && renamed > 0)
                log.info("Bills renamed off staff codes: {}", renamed);
        } catch (Exception e) {
            log.warn("Could not rename bills numbered by staff code: {}", e.getMessage());
        }
    }

    private int rename() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT so.uid, so.branch_uid, so.sales_code, so.staff_code, so.payment_status,
                   bs.first_name, bs.last_name
            FROM sales_opened so
            LEFT JOIN bar_staffs bs ON bs.uid = so.staff_uid
            WHERE so.staff_code IS NOT NULL AND so.staff_code <> ''
              AND so.sales_code LIKE so.staff_code || '-%'
            """);
        if (rows.isEmpty())
            return 0;

        // Unpaid bills keep distinct codes in a branch (the till picks a bill by it).
        Map<String, Set<String>> open = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT branch_uid, sales_code FROM sales_opened WHERE payment_status = 'PENDING' AND sales_code IS NOT NULL"))
            open.computeIfAbsent(String.valueOf(r.get("branch_uid")), k -> new HashSet<>()).add((String) r.get("sales_code"));

        int n = 0;
        for (Map<String, Object> r : rows) {
            String code = (String) r.get("sales_code");
            String staffCode = (String) r.get("staff_code");
            String next = initials((String) r.get("first_name"), (String) r.get("last_name"))
                    + code.substring(staffCode.length());
            if ("PENDING".equals(r.get("payment_status"))) {
                Set<String> held = open.computeIfAbsent(String.valueOf(r.get("branch_uid")), k -> new HashSet<>());
                held.remove(code);
                String candidate = next;
                for (char c = 'A'; held.contains(candidate) && c <= 'Z'; c++)
                    candidate = next + c;
                next = candidate;
                held.add(next);
            }
            String uid = (String) r.get("uid");
            jdbc.update("UPDATE sales_opened SET sales_code = ? WHERE uid = ?", next, uid);
            jdbc.update("UPDATE staff_orders SET sales_code = ? WHERE sales_opened_uid = ?", next, uid);
            jdbc.update("UPDATE bill_line_voids SET sales_code = ? WHERE bill_uid = ?", next, uid);
            n++;
        }
        return n;
    }

    /** As StaffSellService.billPrefix: first letters of first and last name, else "B". */
    static String initials(String first, String last) {
        StringBuilder b = new StringBuilder();
        for (String part : new String[]{first, last}) {
            if (part == null)
                continue;
            String letters = part.replaceAll("[^\\p{L}]", "");
            if (!letters.isEmpty())
                b.append(Character.toUpperCase(letters.charAt(0)));
        }
        return b.length() == 0 ? "B" : b.toString();
    }
}

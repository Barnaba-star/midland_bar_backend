package com.midland.bar.Bar.MyCommission;

import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Repository.BarStaffRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Config.Security.StaffSession;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * A waiter's own commission at Staff Sell (owner, 2026-11-11): today, this
 * week, this month and the last 14 days - earned (bonuses included), paid,
 * shortages and what is still owed to them.
 *
 * Signed in with code + PIN: always their own (staffUid is ignored). At a
 * till opened by a manager (keypad): the staff whose code was typed, sent as
 * staffUid, in this branch only.
 */
@RestController
@RequestMapping("/bar/staffSell")
@RequiredArgsConstructor
public class MyCommissionController {

    private static final int DAYS = 14;

    private final EntityManager em;
    private final BarStaffRepository barStaffRepository;

    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @GetMapping("/myCommission")
    public ResponseEntity<?> myCommission(@RequestParam(required = false) String staffUid) {
        String branch = LoggerUser.getBranchUIDOrMain();
        String who = StaffSession.active() ? StaffSession.staffUid() : staffUid;
        if (who == null || who.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Chagua mhudumu"));
        Optional<BarStaff> staff = barStaffRepository.findBarStaffByUID(who, branch);
        if (staff.isEmpty())
            return ResponseEntity.status(404).body(Map.of("message", "Mhudumu hapatikani"));

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);
        // Last month and this year too (owner, 2026-11-12).
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate from = yearStart.isBefore(lastMonthStart) ? yearStart : lastMonthStart;
        if (today.minusDays(DAYS - 1).isBefore(from))
            from = today.minusDays(DAYS - 1);

        List<Object[]> rows = em.createQuery("""
                SELECT s.createdAt, COALESCE(s.totalAmount, 0), COALESCE(s.payedAmount, 0), COALESCE(s.lossAmount, 0),
                       COALESCE(s.bonusAmount, 0), COALESCE(s.remainingAmount, 0)
                FROM StaffCommissions s
                WHERE s.barStaff.uid = :staff AND s.branchUid = :branch AND s.isActive = true AND s.createdAt >= :from
                ORDER BY s.createdAt DESC
                """, Object[].class)
                .setParameter("staff", who).setParameter("branch", branch).setParameter("from", from)
                .getResultList();

        long[] tday = new long[5], week = new long[5], month = new long[5], lastMonth = new long[5], year = new long[5];
        List<Map<String, Object>> days = new ArrayList<>();
        for (Object[] r : rows) {
            LocalDate d = (LocalDate) r[0];
            long[] v = {num(r[1]), num(r[2]), num(r[3]), num(r[4]), num(r[5])};
            if (d.equals(today)) add(tday, v);
            if (!d.isBefore(weekStart)) add(week, v);
            if (!d.isBefore(monthStart)) add(month, v);
            if (!d.isBefore(lastMonthStart) && d.isBefore(monthStart)) add(lastMonth, v);
            if (!d.isBefore(yearStart)) add(year, v);
            if (!d.isBefore(today.minusDays(DAYS - 1))) {
                Map<String, Object> m = sums(v);
                m.put("date", d.toString());
                days.add(m);
            }
        }
        // Still owed from before this month too - what they will be paid.
        Number owedAll = em.createQuery("""
                SELECT COALESCE(SUM(s.remainingAmount), 0) FROM StaffCommissions s
                WHERE s.barStaff.uid = :staff AND s.branchUid = :branch AND s.isActive = true AND s.remainingAmount > 0
                """, Number.class).setParameter("staff", who).setParameter("branch", branch).getSingleResult();

        BarStaff s = staff.get();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", (Objects.toString(s.getFirstName(), "") + " " + Objects.toString(s.getLastName(), "")).trim());
        out.put("today", sums(tday));
        out.put("week", sums(week));
        out.put("month", sums(month));
        out.put("lastMonth", sums(lastMonth));
        out.put("year", sums(year));
        out.put("owedAll", owedAll == null ? 0 : owedAll.longValue());
        out.put("days", days);
        return ResponseEntity.ok(Map.of("data", out));
    }

    private static void add(long[] into, long[] v) {
        for (int i = 0; i < v.length; i++) into[i] += v[i];
    }

    private static Map<String, Object> sums(long[] v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("earned", v[0]);
        m.put("paid", v[1]);
        m.put("loss", v[2]);
        m.put("bonus", v[3]);
        m.put("owed", v[4]);
        return m;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
}

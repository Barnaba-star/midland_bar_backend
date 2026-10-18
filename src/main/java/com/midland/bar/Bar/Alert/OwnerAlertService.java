package com.midland.bar.Bar.Alert;

import com.midland.bar.Notification.Service.NotificationService;
import com.midland.bar.Notification.Sms.Service.SmsService;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Model.SystemSetting;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Setting.Repository.SystemSettingRepository;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * What the owner hears without asking (owner, 2026-10-18): a morning summary
 * of yesterday, and alerts when something looks wrong - many items taken off
 * bills in one shift, a cash-up short, a shift left open. To the branch's
 * CEO(s) - its MANAGER(s) when it has no CEO - as a notification (the bell),
 * and by SMS once SMS is on. Thresholds are the branch's (POS Setting > Tawi).
 * Never throws: an alert that fails must not undo the work it is about.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerAlertService {

    public static final String SMS_ALERT = "OWNER_ALERT";
    public static final String SMS_DIGEST = "OWNER_DIGEST";

    private final NotificationService notificationService;
    private final SmsService smsService;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public SystemSetting rules(String branchUid) {
        return systemSettingRepository.findByBranchUid(branchUid).orElseGet(SystemSetting::new);
    }

    /** Items taken off bills by one login in their open shift reached the threshold (said once, at the threshold). */
    public void voidsReached(String branchUid, String who, int count) {
        if (count != rules(branchUid).alertVoidsOrDefault())
            return;
        send(branchUid, "ALERTS.VOIDS_TITLE", "ALERTS.VOIDS_MESSAGE", Map.of("who", who, "n", count), "/pos/barReports",
                String.format("%s ameondoa vitu %d kwenye bili katika zamu moja. Angalia Ripoti > Kufunga Zamu.", who, count));
    }

    /** A cash-up came in short by more than the threshold. */
    public void cashUpShort(String branchUid, String who, long variance) {
        if (variance >= 0 || -variance < rules(branchUid).alertShortageOrDefault())
            return;
        send(branchUid, "ALERTS.SHORT_TITLE", "ALERTS.SHORT_MESSAGE", Map.of("who", who, "amount", tzs(-variance)), "/pos/barReports",
                String.format("Cash-up ya %s ina upungufu wa Tsh %s.", who, tzs(-variance)));
    }

    public void shiftOpenLong(String branchUid, String who, long hours) {
        send(branchUid, "ALERTS.LONG_TITLE", "ALERTS.LONG_MESSAGE", Map.of("who", who, "h", hours), "/pos/barReports",
                String.format("Zamu ya %s iko wazi kwa saa %d bila kufungwa.", who, hours));
    }

    /** Yesterday in one message, to every branch that did something. */
    public int morningDigest() {
        LocalDate day = LocalDate.now().minusDays(1);
        int sent = 0;
        for (Branch b : branchRepository.findAll()) {
            try {
                if (Boolean.FALSE.equals(b.getIsActive()))
                    continue;
                Map<String, Object> d = digestOf(b.getUid(), day);
                if (((Number) d.get("sales")).longValue() == 0 && ((Number) d.get("bills")).longValue() == 0)
                    continue;
                d.put("day", day.toString());
                String sms = String.format("%s jana: Mauzo Tsh %s (bili %s). Upungufu cash-up Tsh %s. Vimeondolewa %s (Tsh %s). Refund Tsh %s. Punguzo Tsh %s. Madeni Tsh %s. Stock chini: %s.",
                        b.getBranchName(), tzs(d.get("sales")), d.get("bills"), tzs(d.get("short")), d.get("voids"), tzs(d.get("voidAmount")),
                        tzs(d.get("refunds")), tzs(d.get("discounts")), tzs(d.get("owed")), d.get("lowStock"));
                Map<String, Object> params = new LinkedHashMap<>();
                d.forEach((k, v) -> params.put(k, v instanceof Number n && !"bills".equals(k) && !"voids".equals(k) && !"lowStock".equals(k) ? tzs(n) : v));
                send(b.getUid(), "ALERTS.DIGEST_TITLE", "ALERTS.DIGEST_MESSAGE", params, "/pos", sms);
                sent++;
            } catch (Exception e) {
                log.warn("Morning digest failed for branch {}: {}", b.getUid(), e.getMessage());
            }
        }
        return sent;
    }

    public Map<String, Object> digestOf(String branchUid, LocalDate day) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("b", branchUid)
                .addValue("from", day.atStartOfDay())
                .addValue("to", day.plusDays(1).atStartOfDay())
                .addValue("day", day);
        Map<String, Object> d = new LinkedHashMap<>();
        Map<String, Object> s = jdbc.queryForMap("SELECT COALESCE(SUM(line_total),0) AS t, COUNT(DISTINCT sales_opened) AS n FROM bar_sales " +
                "WHERE branch_uid = :b AND is_active = true AND sold_at >= :from AND sold_at < :to", p);
        d.put("sales", s.get("t"));
        d.put("bills", s.get("n"));
        d.put("short", jdbc.queryForObject("SELECT COALESCE(-SUM(variance),0) FROM cash_ups WHERE branch_uid = :b AND is_active = true " +
                "AND created_at = :day AND variance < 0", p, Long.class));
        Map<String, Object> v = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(SUM(amount),0) AS t FROM bill_line_voids " +
                "WHERE branch_uid = :b AND is_active = true AND voided_at >= :from AND voided_at < :to AND reason NOT LIKE 'Refund:%'", p);
        d.put("voids", v.get("n"));
        d.put("voidAmount", v.get("t"));
        d.put("refunds", jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM bill_refunds WHERE branch_uid = :b AND status = 'APPROVED' " +
                "AND decided_at >= :from AND decided_at < :to", p, Long.class));
        d.put("discounts", jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM bill_discounts WHERE branch_uid = :b " +
                "AND given_at >= :from AND given_at < :to", p, Long.class));
        d.put("owed", jdbc.queryForObject("SELECT COALESCE(SUM(GREATEST(bal,0)),0) FROM (SELECT SUM(CASE WHEN kind='CHARGE' THEN amount ELSE -amount END) AS bal " +
                "FROM debt_entries WHERE branch_uid = :b AND is_active = true GROUP BY customer_uid) x", p, Long.class));
        d.put("lowStock", jdbc.queryForObject("SELECT COUNT(*) FROM bar_services WHERE branch_uid = :b AND is_active = true AND track_stock = true " +
                "AND COALESCE(kind,'') <> 'STOCK_ITEM' AND COALESCE(stock_quantity,0) <= 5 * GREATEST(COALESCE(units_per_pack,1),1)", p, Long.class));
        return d;
    }

    /** Shifts open in the branch right now. */
    public long openShifts(String branchUid) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM work_shifts WHERE branch_uid = :b AND is_active = true AND status = 'OPEN'",
                new MapSqlParameterSource("b", branchUid), Long.class);
    }

    private void send(String branchUid, String titleKey, String messageKey, Map<String, Object> params, String route, String sms) {
        try {
            for (User u : owners(branchUid)) {
                notificationService.notify(u.getUid(), titleKey, messageKey, params, "warning", route);
                if (u.getPhone() != null && !u.getPhone().isBlank())
                    smsService.send(titleKey.endsWith("DIGEST_TITLE") ? SMS_DIGEST : SMS_ALERT, u.getUid(), u.getPhone(), "BaronixTZ: " + sms, true);
            }
        } catch (Exception e) {
            log.warn("Owner alert {} failed for branch {}: {}", titleKey, branchUid, e.getMessage());
        }
    }

    private List<User> owners(String branchUid) {
        List<User> all = userRepository.findAllUsersWithBranchAndRoles(branchUid);
        List<User> ceos = all.stream().filter(u -> hasRole(u, "CEO")).toList();
        return ceos.isEmpty() ? all.stream().filter(u -> hasRole(u, "MANAGER")).toList() : ceos;
    }

    private static boolean hasRole(User u, String code) {
        return u.getRoles() != null && u.getRoles().stream().anyMatch(r -> code.equalsIgnoreCase(r.getCode()) || code.equalsIgnoreCase(r.getName()));
    }

    private static String tzs(Object n) {
        long v = n instanceof Number x ? x.longValue() : 0;
        return NumberFormat.getIntegerInstance(Locale.US).format(v);
    }
}

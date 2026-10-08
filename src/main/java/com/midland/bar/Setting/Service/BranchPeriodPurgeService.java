package com.midland.bar.Setting.Service;

import com.midland.bar.Config.Security.AuthChecker;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Model.Branch;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clears one branch's day-to-day records for a period, to keep the database
 * small: sales, bills and their payments and voids, staff orders and losses, stock adjustments, shifts, cash-ups, store counts and receipts,
 * income and expenses, commissions worked out, the audit and error trail and
 * the branch's messages - every row whose date falls from {@code from} to
 * {@code to}, both days included.
 *
 * What the branch runs on stays: its products, staff, store, users,
 * settings and subscription payments. Unlike deleting the branch,
 * a branch is never started over, it only lets go of history.
 *
 * ROOT only. A dry run counts without touching anything; the real run needs
 * the branch code typed back. One transaction: all of it goes, or none.
 */
@Service
@Log
@RequiredArgsConstructor
public class BranchPeriodPurgeService {

    /** A row of this branch dated inside the period. */
    private static final String OWN = "(branch_uid = :branch AND created_at BETWEEN :from AND :to)";

    /**
     * Children before parents. A child also goes when its parent goes, even
     * if the child itself is dated outside the period - otherwise its
     * foreign key would be left pointing at nothing.
     * {table, extra condition or null}; {X} stands for table X's full rule.
     */
    private static final List<String[]> STEPS = List.of(
            new String[]{"service_store_report", "bar_report_uid IN (SELECT uid FROM bar_reports WHERE {bar_reports})"},
            new String[]{"bar_reports", "bar_sales IN (SELECT uid FROM bar_sales WHERE {bar_sales})"},
            new String[]{"bar_sales", "sales_opened IN (SELECT uid FROM sales_opened WHERE {sales_opened})"},
            new String[]{"bill_payments", "sales_opened_uid IN (SELECT uid FROM sales_opened WHERE {sales_opened})"},
            new String[]{"bill_line_voids", null},
            new String[]{"sales_opened", null},
            new String[]{"staff_order_lines", "staff_order_uid IN (SELECT uid FROM staff_orders WHERE {staff_orders})"},
            new String[]{"staff_orders", null},
            new String[]{"staff_losses", null},
            new String[]{"stock_adjustments", null},
            new String[]{"offline_ops", null},
            new String[]{"staff_commissions", null},
            new String[]{"stock_and_purchase_descriptions", "stock_and_purchase IN (SELECT uid FROM stock_and_purchase WHERE {stock_and_purchase})"},
            new String[]{"stock_and_purchase", null},
            new String[]{"stock_take_lines", "stock_take_uid IN (SELECT uid FROM stock_takes WHERE {stock_takes})"},
            new String[]{"stock_takes", null},
            new String[]{"stock_receipts", null},
            new String[]{"cash_up_lines", "cash_up_uid IN (SELECT uid FROM cash_ups WHERE {cash_ups})"},
            new String[]{"cash_ups", null},
            new String[]{"income_expenses_description", "income_expenses_uid IN (SELECT uid FROM income_expenses WHERE {income_expenses})"},
            new String[]{"income_expenses", null},
            new String[]{"other_commission_entries", null},
            new String[]{"work_shifts", null},
            new String[]{"branch_message_replies", "message_uid IN (SELECT uid FROM branch_messages WHERE {branch_messages})"},
            new String[]{"branch_messages", null},
            new String[]{"audit_logs", null},
            new String[]{"error_logs", null}
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final BranchRepository branchRepository;
    private final AuthChecker authChecker;

    /**
     * Returns rows per table - what would go (dry run) or what went.
     * Codes, not sentences, for the screens to translate: ROOT_ONLY,
     * BRANCH_NOT_FOUND, BAD_PERIOD, CONFIRM_CODE, BLOCKED.
     */
    @Transactional
    public Response<Map<String, Integer>> purge(String branchUID, LocalDate from, LocalDate to,
                                                String confirmCode, boolean dryRun) {
        if (!Boolean.TRUE.equals(authChecker.hasPermissionOrRoot("ROOT"))) {
            return new Response<>("ROOT_ONLY");
        }
        Branch branch = branchUID == null ? null : branchRepository.findById(branchUID).orElse(null);
        if (branch == null) {
            return new Response<>("BRANCH_NOT_FOUND");
        }
        if (from == null || to == null || from.isAfter(to)) {
            return new Response<>("BAD_PERIOD");
        }
        if (!dryRun && (confirmCode == null || !confirmCode.trim().equalsIgnoreCase(branch.getBranchCode()))) {
            return new Response<>("CONFIRM_CODE");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("branch", branchUID)
                .addValue("from", from)
                .addValue("to", to);

        Map<String, String> rules = rules(dated(), existing());
        Map<String, Integer> rows = new LinkedHashMap<>();

        if (!dryRun) {
            log.warning(who() + " is clearing branch " + branch.getBranchCode()
                    + " records from " + from + " to " + to);
        }
        try {
            for (String[] step : STEPS) {
                String table = step[0];
                if (!rules.containsKey(table)) {
                    continue;
                }
                String where = rules.get(table);
                int n = dryRun
                        ? jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, params, Integer.class)
                        : jdbc.update("DELETE FROM " + table + " WHERE " + where, params);
                if (n > 0) {
                    rows.put(table, n);
                }
            }
        } catch (DataIntegrityViolationException e) {
            // Something outside the list still points at these rows. Nothing
            // is half-done: the whole run is undone.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.warning("Branch period purge blocked: " + e.getMostSpecificCause().getMessage());
            return new Response<>("BLOCKED");
        }
        if (!dryRun) {
            log.warning("Branch " + branch.getBranchCode() + " cleared " + from + ".." + to + ": " + rows);
        }
        return new Response<>(rows);
    }

    /**
     * Each table's full rule, with {X} filled in - only for tables this
     * database has. A line table without a branch or date of its own (message
     * replies) goes by its parent alone.
     */
    private static Map<String, String> rules(Set<String> dated, Set<String> existing) {
        Map<String, String> rules = new HashMap<>();
        // Parents are filled first (the list runs children-first, so walk it backwards).
        for (int i = STEPS.size() - 1; i >= 0; i--) {
            String table = STEPS.get(i)[0];
            String extra = STEPS.get(i)[1];
            if (!existing.contains(table)) {
                continue;
            }
            String own = dated.contains(table) ? OWN : null;
            String viaParent = null;
            if (extra != null) {
                String parent = extra.substring(extra.indexOf('{') + 1, extra.indexOf('}'));
                if (rules.containsKey(parent)) {
                    viaParent = extra.replace("{" + parent + "}", rules.get(parent));
                }
            }
            if (own == null && viaParent == null) {
                continue;
            }
            rules.put(table, own == null ? "(" + viaParent + ")"
                    : viaParent == null ? own
                    : "(" + own + " OR " + viaParent + ")");
        }
        return rules;
    }

    /** For the log only - never a reason for the run itself to fail. */
    private static String who() {
        try {
            return LoggerUser.getEmail();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private Set<String> existing() {
        return new HashSet<>(jdbc.getJdbcTemplate().queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()",
                String.class));
    }

    /** Tables that carry both branch_uid and created_at. */
    private Set<String> dated() {
        return new HashSet<>(jdbc.getJdbcTemplate().queryForList(
                "SELECT table_name FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND column_name IN ('branch_uid', 'created_at') "
                        + "GROUP BY table_name HAVING COUNT(DISTINCT column_name) = 2",
                String.class));
    }
}

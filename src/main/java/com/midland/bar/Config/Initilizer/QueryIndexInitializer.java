package com.midland.bar.Config.Initilizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates the indexes the hot queries actually filter on.
 *
 * The entities only declare single-column indexes on branch_uid and
 * is_active, but nearly every query is "this branch AND this day/status", or
 * looks a bill's lines up by the bill. Postgres does not index foreign keys
 * by itself, so without these, opening one bill reads every bar_sales row,
 * and a report for a day reads the whole branch's history.
 *
 * Built CONCURRENTLY so a deploy never locks the tables against sales, and
 * IF NOT EXISTS so this is a no-op on every later startup. A concurrent
 * build that died half way leaves an INVALID index behind which IF NOT
 * EXISTS would skip forever, so those are dropped and built again.
 *
 * Keep this list in step with the repositories: a new query that filters on
 * columns no index starts with belongs here.
 */
@Component
@Log
@Order(110) // after SearchIndexInitializer
@RequiredArgsConstructor
public class QueryIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    /** name, table, then the index body: columns and an optional WHERE. */
    private record QueryIndex(String name, String table, String body) {}

    private static final List<QueryIndex> INDEXES = List.of(
            // A bill's lines: billTotal, findBarSalesList, findLinesOfBill, countLines.
            new QueryIndex("idx_bar_sales_bill", "bar_sales", "(sales_opened)"),
            // Sales of a day / range in a branch (history, insights).
            new QueryIndex("idx_bar_sales_branch_day", "bar_sales", "(branch_uid, created_at)"),
            // Last sale of a product (insights "not selling").
            new QueryIndex("idx_bar_sales_service_day", "bar_sales", "(bar_service_uid, created_at)"),

            // Every revenue/report query: branch + day or range.
            new QueryIndex("idx_bar_reports_branch_day", "bar_reports", "(branch_uid, created_at)"),
            new QueryIndex("idx_bar_reports_line", "bar_reports", "(bar_sales)"),
            new QueryIndex("idx_service_store_report_report", "service_store_report", "(bar_report_uid)"),
            new QueryIndex("idx_service_store_report_open", "service_store_report", "(store_open_uid)"),

            // Open bills (PENDING) - the Sales page, per waiter, counts.
            new QueryIndex("idx_sales_open_branch_status", "sales_opened", "(branch_uid, payment_status, staff_uid)"),
            new QueryIndex("idx_sales_open_branch_day", "sales_opened", "(branch_uid, created_at)"),
            // Phone payments page: only bills that carry a note.
            new QueryIndex("idx_sales_open_notes", "sales_opened",
                    "(branch_uid, payment_note_at) WHERE payment_note_method IS NOT NULL"),

            new QueryIndex("idx_staff_commissions_branch_day", "staff_commissions", "(branch_uid, created_at)"),
            new QueryIndex("idx_staff_commissions_staff_day", "staff_commissions", "(bar_staff_uid, created_at)"),

            new QueryIndex("idx_stock_and_purchase_branch_week", "stock_and_purchase", "(branch_uid, week_date)"),
            new QueryIndex("idx_stock_and_purchase_service_week", "stock_and_purchase", "(bar_service, week_date)"),
            new QueryIndex("idx_stock_and_purchase_desc_parent", "stock_and_purchase_descriptions", "(stock_and_purchase)"),
            new QueryIndex("idx_stock_receipts_service_at", "stock_receipts", "(bar_service_uid, received_at)"),

            new QueryIndex("idx_income_expenses_branch_week", "income_expenses", "(branch_uid, week_start_date)"),
            new QueryIndex("idx_income_expenses_desc_parent", "income_expenses_description", "(income_expenses_uid)"),
            new QueryIndex("idx_income_expenses_desc_paid", "income_expenses_description", "(branch_uid, paid_by, paid_at)"),

            new QueryIndex("idx_store_open_branch_status", "store_open", "(branch_uid, status)"),
            new QueryIndex("idx_store_open_store", "store_open", "(store_uid)"),
            new QueryIndex("idx_store_service", "store", "(bar_service_entity_uid)"),

            new QueryIndex("idx_staff_order_lines_order", "staff_order_lines", "(staff_order_uid)"),
            new QueryIndex("idx_staff_order_lines_return", "staff_order_lines",
                    "(return_of_sales_uid) WHERE return_of_sales_uid IS NOT NULL"),
            new QueryIndex("idx_staff_orders_staff_status", "staff_orders", "(staff_uid, status)"),

            // Staff code sign-in compares UPPER(staff_code), across branches too.
            new QueryIndex("idx_bar_staffs_code_upper", "bar_staffs", "(UPPER(staff_code))"),
            new QueryIndex("idx_bar_staffs_user", "bar_staffs", "(user_uid)"),

            new QueryIndex("idx_bill_refund_bill", "bill_refunds", "(bill_uid)"),
            new QueryIndex("idx_bill_discount_bill", "bill_discounts", "(bill_uid)"),
            new QueryIndex("idx_offer_use_bill", "offer_uses", "(bill_uid)"),
            new QueryIndex("idx_sdi_entry", "supplier_delivery_items", "(entry_uid)"),

            // Join tables with no primary key, read on every sign-in and
            // every request's permission check (User/Role are EAGER).
            new QueryIndex("idx_user_roles_user", "user_roles", "(user_uid)"),
            new QueryIndex("idx_role_permission_role", "role_permission", "(role_uid)"),

            new QueryIndex("idx_notification_unread", "notifications", "(target_user_uid, is_read)")
    );

    @Override
    public void run(ApplicationArguments args) {
        log.info("************************Query Indexes******************************");
        int built = 0;
        for (QueryIndex index : INDEXES) {
            if (create(index)) built++;
        }
        log.info("Query indexes checked: " + INDEXES.size() + ", built now: " + built);
    }

    /** True when the index was built on this startup. */
    private boolean create(QueryIndex index) {
        try {
            Boolean valid = jdbcTemplate.query(
                    "SELECT i.indisvalid FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid "
                            + "WHERE c.relname = ? AND c.relnamespace = current_schema()::regnamespace",
                    rs -> rs.next() ? rs.getBoolean(1) : null,
                    index.name());
            if (Boolean.TRUE.equals(valid)) return false;
            if (Boolean.FALSE.equals(valid)) {
                log.warning("Rebuilding invalid index " + index.name());
                jdbcTemplate.execute("DROP INDEX CONCURRENTLY IF EXISTS " + index.name());
            }
            jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS " + index.name()
                    + " ON " + index.table() + " " + index.body());
            return true;
        } catch (Exception e) {
            // A table or column not created yet (fresh database) should not
            // stop the rest, nor the startup.
            log.warning("Could not create " + index.name() + ": " + e.getMessage());
            return false;
        }
    }
}

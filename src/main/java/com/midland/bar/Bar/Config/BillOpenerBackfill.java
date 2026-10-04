package com.midland.bar.Bar.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Bills opened before sales_opened.opened_by existed have no opener. Their
 * first line's seller is who opened them at the till (lines on a bill with no
 * staff member are sold as the login), so that is filled in once, and the
 * name looked up from users. Idempotent: only empty rows are touched, so each
 * later start finds nothing to do.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillOpenerBackfill implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int openers = jdbc.update("""
                UPDATE sales_opened so
                SET opened_by = first_line.sold_by
                FROM (
                    SELECT DISTINCT ON (sales_opened) sales_opened, sold_by
                    FROM bar_sales
                    WHERE sold_by IS NOT NULL
                    ORDER BY sales_opened, sold_at NULLS LAST
                ) first_line
                WHERE first_line.sales_opened = so.uid
                  AND so.opened_by IS NULL
                  AND so.staff_code IS NULL
                """);
            int names = jdbc.update("""
                UPDATE sales_opened so
                SET opened_by_name = COALESCE(
                    NULLIF(TRIM(CONCAT_WS(' ', NULLIF(TRIM(u.first_name), ''), NULLIF(TRIM(u.middle_name), ''), NULLIF(TRIM(u.last_name), ''))), ''),
                    so.opened_by)
                FROM users u
                WHERE LOWER(u.username) = LOWER(so.opened_by)
                  AND so.opened_by IS NOT NULL
                  AND so.opened_by_name IS NULL
                """);
            if (openers > 0 || names > 0) {
                log.info("Bill openers filled in: {} bills, {} names", openers, names);
            }
        } catch (Exception e) {
            // A cosmetic label: never worth stopping the app over.
            log.warn("Could not fill in bill openers: {}", e.getMessage());
        }
    }
}

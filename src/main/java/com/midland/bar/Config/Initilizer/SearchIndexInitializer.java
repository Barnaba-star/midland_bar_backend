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
 * Creates the trigram indexes behind the search boxes.
 *
 * The search queries match with LIKE '%term%', which a plain B-tree index can
 * never serve - without these, every keystroke is a sequential scan of the
 * whole table. pg_trgm indexes the three-character sequences in a column, so
 * a substring match becomes an index lookup.
 *
 * ddl-auto=update cannot express a GIN index, and the project has no
 * migration tool, so this runs here instead - idempotent, and safe to run on
 * every startup.
 */
@Component
@Log
@Order(100) // after Initializer's seeding; nothing here depends on it, but the log reads better
@RequiredArgsConstructor
public class SearchIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    /**
     * One index per searched column. A single OR'd query can use several of
     * them at once (Postgres combines them with a BitmapOr), but only if
     * EVERY branch of the OR is indexed - one unindexed column would drag the
     * whole query back to a sequential scan. So this list has to stay in step
     * with the LIKE columns in BranchRepository.searchBranchPage and
     * UserRepository.findUsers.
     *
     * The expression must match the query exactly: the queries compare on
     * LOWER(column), so the index is on LOWER(column) too.
     */
    private static final List<String> INDEXED_COLUMNS = List.of(
            "branches:branch_name",
            "branches:branch_code",
            "branches:region",
            "branches:phone",
            "users:first_name",
            "users:middle_name",
            "users:last_name",
            "users:username",
            "users:email",
            "users:phone"
    );

    @Override
    public void run(ApplicationArguments args) {
        log.info("************************Search Indexes******************************");

        if (!enableTrigramExtension()) {
            // Search still works, just without index support. Not worth
            // failing startup over.
            log.warning("pg_trgm unavailable - search will fall back to sequential scans");
            return;
        }

        for (String entry : INDEXED_COLUMNS) {
            String[] parts = entry.split(":");
            createTrigramIndex(parts[0], parts[1]);
        }
    }

    private boolean enableTrigramExtension() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            return true;
        } catch (Exception e) {
            // Managed Postgres may not grant the rights to install it.
            log.warning("Could not enable pg_trgm: " + e.getMessage());
            return false;
        }
    }

    private void createTrigramIndex(String table, String column) {
        String indexName = "idx_trgm_" + table + "_" + column;
        String sql = "CREATE INDEX IF NOT EXISTS " + indexName
                + " ON " + table + " USING GIN (LOWER(" + column + ") gin_trgm_ops)";
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            // A column that has not been created yet (fresh database, first
            // boot ordering) should not stop the rest.
            log.warning("Could not create " + indexName + ": " + e.getMessage());
        }
    }
}

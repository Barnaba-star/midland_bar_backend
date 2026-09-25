package com.midland.bar.Setting.Repository;

import com.midland.bar.Setting.Dto.TableSizeDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DatabaseMonitoringRepository {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMonitoringRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TableSizeDto> findTableSizes() {

        String sql = """
                SELECT
                    schemaname,
                    relname AS table_name,
                    n_live_tup AS row_count,

                    pg_relation_size(relid)
                        AS table_size_bytes,

                    pg_indexes_size(relid)
                        AS index_size_bytes,

                    pg_total_relation_size(relid)
                        AS total_size_bytes,

                    pg_size_pretty(
                        pg_relation_size(relid)
                    ) AS table_size,

                    pg_size_pretty(
                        pg_indexes_size(relid)
                    ) AS index_size,

                    pg_size_pretty(
                        pg_total_relation_size(relid)
                    ) AS total_size

                FROM pg_stat_user_tables

                ORDER BY
                    pg_total_relation_size(relid) DESC
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new TableSizeDto(
                        rs.getString("schemaname"),
                        rs.getString("table_name"),
                        rs.getLong("row_count"),
                        rs.getLong("table_size_bytes"),
                        rs.getLong("index_size_bytes"),
                        rs.getLong("total_size_bytes"),
                        rs.getString("table_size"),
                        rs.getString("index_size"),
                        rs.getString("total_size")
                )
        );
    }
}
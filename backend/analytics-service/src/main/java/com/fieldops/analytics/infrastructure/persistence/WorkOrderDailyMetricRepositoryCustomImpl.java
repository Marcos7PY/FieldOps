package com.fieldops.analytics.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Repository
public class WorkOrderDailyMetricRepositoryCustomImpl implements WorkOrderDailyMetricRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    private Boolean isSqlServer;

    public WorkOrderDailyMetricRepositoryCustomImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private boolean isSqlServer() {
        if (isSqlServer == null) {
            var dataSource = jdbcTemplate.getDataSource();
            if (dataSource != null) {
                try (var conn = dataSource.getConnection()) {
                    String name = conn.getMetaData().getDatabaseProductName();
                    isSqlServer = name != null && name.toLowerCase().contains("microsoft");
                } catch (java.sql.SQLException e) {
                    isSqlServer = false;
                }
            } else {
                isSqlServer = false;
            }
        }
        return Boolean.TRUE.equals(isSqlServer);
    }

    @Override
    public void upsertMetric(
            LocalDate metricDate,
            Long technicianId,
            String status,
            int count,
            BigDecimal avgDuration,
            LocalDateTime updatedAt
    ) {
        if (isSqlServer()) {
            upsertWithSqlServerMerge(metricDate, technicianId, status, count, avgDuration, updatedAt);
        } else {
            upsertForH2(metricDate, technicianId, status, count, avgDuration, updatedAt);
        }
    }

    private void upsertWithSqlServerMerge(
            LocalDate metricDate,
            Long technicianId,
            String status,
            int count,
            BigDecimal avgDuration,
            LocalDateTime updatedAt
    ) {
        String sql = """
                MERGE INTO work_order_daily_metrics WITH (HOLDLOCK) AS target
                USING (VALUES (?, ?, ?, ?, ?, ?)) AS source (metric_date, technician_id, status, order_count, avg_duration_minutes, updated_at)
                ON target.metric_date = source.metric_date
                   AND target.technician_id = source.technician_id
                   AND target.status = source.status
                WHEN MATCHED THEN
                    UPDATE SET
                        target.order_count = target.order_count + source.order_count,
                        target.avg_duration_minutes = CASE
                            WHEN source.avg_duration_minutes IS NOT NULL AND target.avg_duration_minutes IS NOT NULL
                                THEN (target.avg_duration_minutes * target.order_count + source.avg_duration_minutes * source.order_count) / (target.order_count + source.order_count)
                            WHEN source.avg_duration_minutes IS NOT NULL
                                THEN source.avg_duration_minutes
                            ELSE target.avg_duration_minutes
                        END,
                        target.updated_at = source.updated_at
                WHEN NOT MATCHED THEN
                    INSERT (metric_date, technician_id, status, order_count, avg_duration_minutes, updated_at)
                    VALUES (source.metric_date, source.technician_id, source.status, source.order_count, source.avg_duration_minutes, source.updated_at);
                """;

        jdbcTemplate.update(sql,
                Date.valueOf(metricDate),
                technicianId,
                status,
                count,
                avgDuration,
                Timestamp.valueOf(updatedAt)
        );
    }

    private void upsertForH2(
            LocalDate metricDate,
            Long technicianId,
            String status,
            int count,
            BigDecimal avgDuration,
            LocalDateTime updatedAt
    ) {
        String updateSql = """
                UPDATE work_order_daily_metrics
                SET order_count = order_count + ?,
                    avg_duration_minutes = CASE
                        WHEN ? IS NOT NULL AND avg_duration_minutes IS NOT NULL
                            THEN (avg_duration_minutes * order_count + ? * ?) / (order_count + ?)
                        WHEN ? IS NOT NULL
                            THEN ?
                        ELSE avg_duration_minutes
                    END,
                    updated_at = ?
                WHERE metric_date = ? AND technician_id = ? AND status = ?
                """;

        int rows = jdbcTemplate.update(updateSql,
                count,
                avgDuration, avgDuration, count, count,
                avgDuration, avgDuration,
                Timestamp.valueOf(updatedAt),
                Date.valueOf(metricDate), technicianId, status
        );

        if (rows == 0) {
            String insertSql = """
                    INSERT INTO work_order_daily_metrics (metric_date, technician_id, status, order_count, avg_duration_minutes, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;
            jdbcTemplate.update(insertSql,
                    Date.valueOf(metricDate), technicianId, status, count, avgDuration, Timestamp.valueOf(updatedAt)
            );
        }
    }

    @Override
    public void truncateAll() {
        jdbcTemplate.execute("TRUNCATE TABLE work_order_daily_metrics");
    }
}

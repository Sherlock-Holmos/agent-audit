package com.audit.data.service.infrastructure;

import java.sql.SQLException;
import java.util.Objects;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
/**
 * 数据处理相关表结构初始化器：在启动时补齐治理、工作流与审计表结构。
 */
public class DataProcessSchemaInitializer {

    private static final Pattern SAFE_SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final JdbcTemplate jdbcTemplate;
    private final String stagingSchema;

    public DataProcessSchemaInitializer(
        JdbcTemplate jdbcTemplate,
        @Value("${app.datasource.staging-schema:agent_audit_staging}") String stagingSchema
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.stagingSchema = sanitizeSchemaName(stagingSchema);
    }

    @PostConstruct
    public void initialize() {
        ensureStagingSchema();
        ensureCleanStrategyColumns();
        ensureGovernanceTables();
        ensureWorkflowTables();
        ensureAuditActionTable();
    }

    private void ensureStagingSchema() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + stagingSchema);
    }

    private void ensureCleanStrategyColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE clean_strategy_record ADD COLUMN content TEXT");
        } catch (DataAccessException ex) {
            if (!isDuplicateColumnError(ex)) {
                throw ex;
            }
        }

        try {
            jdbcTemplate.execute("ALTER TABLE clean_strategy_record ADD COLUMN remark VARCHAR(512)");
        } catch (DataAccessException ex) {
            if (!isDuplicateColumnError(ex)) {
                throw ex;
            }
        }
    }

    private void ensureGovernanceTables() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS etl_field_lineage (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              tenant_id VARCHAR(128),
              owner_username VARCHAR(128) NOT NULL,
              task_type VARCHAR(32) NOT NULL,
              task_id BIGINT NOT NULL,
              source_table VARCHAR(255) NOT NULL,
              source_field VARCHAR(255) NOT NULL,
              target_table VARCHAR(255) NOT NULL,
              target_field VARCHAR(255) NOT NULL,
              created_at DATETIME NOT NULL,
              INDEX idx_lineage_owner_task (owner_username, task_type, task_id)
            )
            """
        );

        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS etl_quality_report (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              tenant_id VARCHAR(128),
              owner_username VARCHAR(128) NOT NULL,
              task_type VARCHAR(32) NOT NULL,
              task_id BIGINT NOT NULL,
              table_name VARCHAR(255) NOT NULL,
              total_rows INT NOT NULL,
              unknown_rows INT NOT NULL,
              duplicate_rows INT NOT NULL,
              quality_score INT NOT NULL,
              created_at DATETIME NOT NULL,
              INDEX idx_quality_owner_task (owner_username, task_type, task_id)
            )
            """
        );

        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS etl_table_snapshot (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              tenant_id VARCHAR(128),
              owner_username VARCHAR(128) NOT NULL,
              task_type VARCHAR(32) NOT NULL,
              task_id BIGINT NOT NULL,
              table_name VARCHAR(255) NOT NULL,
              snapshot_version INT NOT NULL,
              row_count INT NOT NULL,
              schema_json LONGTEXT,
              created_at DATETIME NOT NULL,
              INDEX idx_snapshot_owner_task (owner_username, task_type, task_id)
            )
            """
        );

        ensureColumnExists("ALTER TABLE etl_field_lineage ADD COLUMN tenant_id VARCHAR(128)");
        ensureColumnExists("ALTER TABLE etl_quality_report ADD COLUMN tenant_id VARCHAR(128)");
        ensureColumnExists("ALTER TABLE etl_table_snapshot ADD COLUMN tenant_id VARCHAR(128)");
    }

    private void ensureWorkflowTables() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS etl_workflow_record (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                tenant_id VARCHAR(128) NOT NULL,
                owner_username VARCHAR(128) NOT NULL,
                workflow_name VARCHAR(255) NOT NULL,
                workflow_json LONGTEXT,
                created_at DATETIME NOT NULL,
                updated_at DATETIME NOT NULL,
                INDEX idx_workflow_owner (tenant_id, owner_username)
            )
            """
        );

        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS etl_workflow_run_record (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                tenant_id VARCHAR(128) NOT NULL,
                owner_username VARCHAR(128) NOT NULL,
                workflow_id BIGINT NOT NULL,
                run_status VARCHAR(32) NOT NULL,
                start_at DATETIME,
                end_at DATETIME,
                error_message VARCHAR(1024),
                created_at DATETIME NOT NULL,
                updated_at DATETIME NOT NULL,
                INDEX idx_workflow_run_owner (tenant_id, owner_username, workflow_id)
            )
            """
        );

        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS etl_workflow_node_run_record (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                run_id BIGINT NOT NULL,
                node_id VARCHAR(128) NOT NULL,
                task_type VARCHAR(32) NOT NULL,
                task_id BIGINT NOT NULL,
                status VARCHAR(32) NOT NULL,
                error_message VARCHAR(1024),
                started_at DATETIME,
                ended_at DATETIME,
                INDEX idx_node_run_run (run_id)
            )
            """
        );
    }

    private void ensureAuditActionTable() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS audit_action_record (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              tenant_id VARCHAR(128),
              actor_username VARCHAR(128) NOT NULL,
              action_type VARCHAR(32) NOT NULL,
              resource_type VARCHAR(64) NOT NULL,
              resource_id VARCHAR(64) NOT NULL,
              result_status VARCHAR(16) NOT NULL,
              detail_json LONGTEXT,
              created_at DATETIME NOT NULL,
              INDEX idx_audit_actor_created (actor_username, created_at)
            )
            """
        );
        ensureColumnExists("ALTER TABLE audit_action_record ADD COLUMN tenant_id VARCHAR(128)");
    }

    private void ensureColumnExists(String alterSql) {
        try {
            jdbcTemplate.execute(Objects.requireNonNull(alterSql));
        } catch (DataAccessException ex) {
            if (!isDuplicateColumnError(ex)) {
                throw ex;
            }
        }
    }

    private boolean isDuplicateColumnError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("duplicate column") ||
                    normalized.contains("already exists") ||
                    normalized.contains("column already exists")) {
                    return true;
                }
            }
            if (current instanceof SQLException sqlEx) {
                if (sqlEx.getErrorCode() == 1060) {
                    return true;
                }
                String sqlState = sqlEx.getSQLState();
                if ("42S21".equalsIgnoreCase(sqlState)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String sanitizeSchemaName(String schemaName) {
        String normalized = schemaName == null ? "" : schemaName.trim();
        if (!SAFE_SCHEMA_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("schema 鍚嶄笉鍚堟硶: " + schemaName);
        }
        return normalized;
    }
}


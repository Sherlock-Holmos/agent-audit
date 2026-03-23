package com.audit.data.service.infrastructure;

import com.audit.data.repository.DataProcessTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
/**
 * 中间表基础设施服务：负责标准表/融合表的创建、装载、合并与回收。
 */
public class StagingTableService {

    private static final Pattern SAFE_TABLE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Pattern SAFE_SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final JdbcTemplate jdbcTemplate;
    private final DataProcessTaskRepository dataProcessTaskRepository;
    private final FileRowReader fileRowReader;
    private final ObjectMapper objectMapper;
    private final String stagingSchema;

    public StagingTableService(
        JdbcTemplate jdbcTemplate,
        DataProcessTaskRepository dataProcessTaskRepository,
        FileRowReader fileRowReader,
        ObjectMapper objectMapper,
        @Value("${app.datasource.staging-schema:agent_audit_staging}") String stagingSchema
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataProcessTaskRepository = dataProcessTaskRepository;
        this.fileRowReader = fileRowReader;
        this.objectMapper = objectMapper;
        this.stagingSchema = sanitizeSchemaName(stagingSchema);
    }

    public void recreateStandardTable(String tableName) {
        String tableRef = stagingTableRef(tableName);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableRef);
                String createSql = Objects.requireNonNull(
                        """
                        CREATE TABLE %s (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            task_id BIGINT NOT NULL,
                            source_id BIGINT NOT NULL,
                            object_name VARCHAR(255) NOT NULL,
                            row_no INT NOT NULL,
                            raw_json LONGTEXT NOT NULL,
                            normalized_json LONGTEXT NOT NULL,
                            created_at DATETIME NOT NULL
                        )
                        """.formatted(tableRef)
                );
                jdbcTemplate.execute(createSql);
    }

    public void recreateFusionTable(String tableName) {
        String tableRef = stagingTableRef(tableName);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableRef);
                String createSql = Objects.requireNonNull(
                        """
                        CREATE TABLE %s (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            fusion_task_id BIGINT NOT NULL,
                            clean_task_id BIGINT NOT NULL,
                            source_id BIGINT NOT NULL,
                            object_name VARCHAR(255) NOT NULL,
                            row_no INT NOT NULL,
                            raw_json LONGTEXT NOT NULL,
                            normalized_json LONGTEXT NOT NULL,
                            source_standard_table VARCHAR(255) NOT NULL,
                            created_at DATETIME NOT NULL
                        )
                        """.formatted(tableRef)
                );
                jdbcTemplate.execute(createSql);
    }

    public void loadObjectsIntoStandardTable(String ownerUsername, Long taskId, List<Map<String, Object>> cleanObjects, String outputTableName) {
        String outputTableRef = stagingTableRef(outputTableName);
        for (Map<String, Object> object : cleanObjects) {
            Long sourceId = toLong(object.get("sourceId"));
            String objectName = text(object.get("objectName"));
            if (sourceId == null || isBlank(objectName)) {
                throw new IllegalArgumentException("娓呮礂瀵硅薄淇℃伅涓嶅畬鏁");
            }

            Map<String, Object> source = getSourceById(ownerUsername, sourceId);
            String sourceType = text(source.get("type")).toUpperCase();
            List<String> rows = switch (sourceType) {
                case "DATABASE" -> readDatabaseRows(objectName);
                case "FILE" -> fileRowReader.readRows(text(source.get("filePath")), text(source.get("fileName")));
                default -> throw new IllegalArgumentException("涓嶆敮鎸佺殑鏁版嵁婧愮被鍨? " + sourceType);
            };

            int rowNo = 1;
            for (String row : rows) {
                jdbcTemplate.update(
                    "INSERT INTO " + outputTableRef + "(task_id,source_id,object_name,row_no,raw_json,normalized_json,created_at) VALUES(?,?,?,?,?,?,?)",
                    taskId,
                    sourceId,
                    objectName,
                    rowNo++,
                    row,
                    row,
                    now()
                );
            }
        }
    }

    public int mergeStandardTablesToTarget(String ownerUsername, Long fusionTaskId, String targetTableName, List<String> standardTables) {
        String targetTableRef = stagingTableRef(targetTableName);
        int total = 0;
        for (String table : standardTables) {
            String safeStandardTable = sanitizeTableName(table);
            String sourceTableRef = stagingTableRef(safeStandardTable);
            Map<String, Object> cleanTask = dataProcessTaskRepository.findCleanTaskByStandardTable(ownerUsername, safeStandardTable);
            Long cleanTaskId = toLong(cleanTask.get("id"));
            if (cleanTaskId == null) {
                throw new IllegalArgumentException("娓呮礂浠诲姟缂哄け: " + safeStandardTable);
            }

                        String mergeSql = Objects.requireNonNull(
                                """
                                INSERT INTO %s(fusion_task_id,clean_task_id,source_id,object_name,row_no,raw_json,normalized_json,source_standard_table,created_at)
                                SELECT ?, ?, source_id, object_name, row_no, raw_json, normalized_json, ?, ?
                                    FROM %s
                                """.formatted(targetTableRef, sourceTableRef)
                        );
                        int inserted = jdbcTemplate.update(
                                mergeSql,
                fusionTaskId,
                cleanTaskId,
                safeStandardTable,
                now()
            );
            total += inserted;
        }
        return total;
    }

    public void dropStandardTableIfUnused(String standardTable) {
        if (isBlank(standardTable)) {
            return;
        }
        String safeTable = safeTableOrNull(standardTable);
        if (safeTable == null) {
            return;
        }

        if (dataProcessTaskRepository.countCleanTaskByStandardTable(safeTable) > 0) {
            return;
        }
        if (dataProcessTaskRepository.countFusionRefByStandardTable(safeTable) > 0) {
            return;
        }

        dropTableIfExists(safeTable);
    }

    public void dropFusionTargetTableIfUnused(String targetTable) {
        if (isBlank(targetTable)) {
            return;
        }
        String safeTable = safeTableOrNull(targetTable);
        if (safeTable == null) {
            return;
        }

        if (dataProcessTaskRepository.countFusionTaskByTargetTable(safeTable) > 0) {
            return;
        }

        dropTableIfExists(safeTable);
    }

    public void dropTableIfExists(String tableName) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + stagingTableRef(tableName));
    }

    public List<String> listAllTables() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema=? ORDER BY table_name",
            stagingSchema
        );
        List<String> tables = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("table_name");
            if (value == null) {
                value = row.get("TABLE_NAME");
            }
            if (value != null) {
                tables.add(String.valueOf(value));
            }
        }
        return tables;
    }

    public boolean isGeneratedTableCandidate(String tableName) {
        String normalized = text(tableName).toLowerCase();
        return normalized.startsWith("clean_std_")
            || normalized.startsWith("fusion_")
            || normalized.startsWith("tmp_fusion_")
            || normalized.startsWith("std_")
            || normalized.startsWith("fuse_");
    }

    private List<String> readDatabaseRows(String objectName) {
        String tableName = sanitizeTableName(objectName);
        List<Map<String, Object>> records = jdbcTemplate.queryForList("SELECT * FROM " + tableName + " LIMIT 10000");
        List<String> rows = new ArrayList<>();
        for (Map<String, Object> record : records) {
            rows.add(toJson(record));
        }
        return rows;
    }

    private Map<String, Object> getSourceById(String ownerUsername, Long sourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,type,file_name,file_path FROM data_source_record WHERE owner_username=? AND id=?",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("type", rs.getString("type"));
                row.put("fileName", nvl(rs.getString("file_name")));
                row.put("filePath", nvl(rs.getString("file_path")));
                return row;
            },
            ownerUsername,
            sourceId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("鏁版嵁婧愪笉瀛樺湪: " + sourceId);
        }
        return rows.get(0);
    }

    private String safeTableOrNull(String tableName) {
        try {
            return sanitizeTableName(tableName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String stagingTableRef(String tableName) {
        return stagingSchema + "." + sanitizeTableName(tableName);
    }

    private String sanitizeSchemaName(String schemaName) {
        String normalized = text(schemaName);
        if (!SAFE_SCHEMA_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("schema 鍚嶄笉鍚堟硶: " + schemaName);
        }
        return normalized;
    }

    private String sanitizeTableName(String tableName) {
        String normalized = text(tableName);
        if (!SAFE_TABLE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("琛ㄥ悕涓嶅悎娉? " + tableName);
        }
        return normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON搴忓垪鍖栧け璐");
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static String now() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.now());
    }
}


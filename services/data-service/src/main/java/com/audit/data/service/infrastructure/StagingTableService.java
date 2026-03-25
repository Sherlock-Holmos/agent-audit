package com.audit.data.service.infrastructure;

import com.audit.data.repository.DataProcessTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
/**
 * 中间表基础设施服务：负责标准表/融合表的创建、装载、合并与回收。
 */
public class StagingTableService {

    private static final Logger log = LoggerFactory.getLogger(StagingTableService.class);
    private static final Pattern SAFE_TABLE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Pattern SAFE_SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final JdbcTemplate jdbcTemplate;
    private final DataProcessTaskRepository dataProcessTaskRepository;
    private final FileRowReader fileRowReader;
    private final ObjectMapper objectMapper;
    private final String stagingSchema;
    private final Map<String, List<String>> defaultKeySynonyms;

    public StagingTableService(
        JdbcTemplate jdbcTemplate,
        DataProcessTaskRepository dataProcessTaskRepository,
        FileRowReader fileRowReader,
        ObjectMapper objectMapper,
        @Value("${app.datasource.staging-schema:agent_audit_staging}") String stagingSchema,
        @Value("${app.fusion.key-synonyms-json:}") String keySynonymsJson
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataProcessTaskRepository = dataProcessTaskRepository;
        this.fileRowReader = fileRowReader;
        this.objectMapper = objectMapper;
        this.stagingSchema = sanitizeSchemaName(stagingSchema);
        this.defaultKeySynonyms = buildKeySynonyms(keySynonymsJson);
        log.info("Fusion key-synonyms loaded: canonicalKeys={}, customJsonConfigured={}",
            this.defaultKeySynonyms.size(),
            !isBlank(keySynonymsJson)
        );
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

    public void recreateRawTable(String tableName) {
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
                throw new IllegalArgumentException("清洗对象信息不完整");
            }

            Map<String, Object> source = getSourceById(ownerUsername, sourceId);
            String sourceType = text(source.get("type")).toUpperCase();
            List<String> rows = switch (sourceType) {
                case "DATABASE" -> readDatabaseRows(objectName);
                case "FILE" -> fileRowReader.readRows(text(source.get("filePath")), text(source.get("fileName")));
                default -> throw new IllegalArgumentException("不支持的数据源类型: " + sourceType);
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

    public void loadObjectsIntoRawTable(String ownerUsername, Long taskId, List<Map<String, Object>> cleanObjects, String rawTableName) {
        String rawTableRef = stagingTableRef(rawTableName);
        for (Map<String, Object> object : cleanObjects) {
            Long sourceId = toLong(object.get("sourceId"));
            String objectName = text(object.get("objectName"));
            if (sourceId == null || isBlank(objectName)) {
                throw new IllegalArgumentException("清洗对象信息不完整");
            }

            List<String> rows = extractRows(ownerUsername, sourceId, objectName);
            int rowNo = 1;
            for (String row : rows) {
                jdbcTemplate.update(
                    "INSERT INTO " + rawTableRef + "(task_id,source_id,object_name,row_no,raw_json,created_at) VALUES(?,?,?,?,?,?)",
                    taskId,
                    sourceId,
                    objectName,
                    rowNo++,
                    row,
                    now()
                );
            }
        }
    }

    public void loadStandardFromRawTable(Long taskId, String rawTableName, String standardTableName) {
        String rawTableRef = stagingTableRef(rawTableName);
        String standardTableRef = stagingTableRef(standardTableName);
        String sql = Objects.requireNonNull(
            """
            INSERT INTO %s(task_id,source_id,object_name,row_no,raw_json,normalized_json,created_at)
            SELECT task_id, source_id, object_name, row_no, raw_json, raw_json, created_at
              FROM %s
             WHERE task_id=?
            """.formatted(standardTableRef, rawTableRef)
        );
        jdbcTemplate.update(sql, taskId);
    }

    public int mergeStandardTablesToTarget(
        String ownerUsername,
        Long fusionTaskId,
        String targetTableName,
        List<String> standardTables,
        String strategy,
        Map<String, Object> fusionConfig
    ) {
        if ("KEY_ALIGN".equalsIgnoreCase(text(strategy))) {
            return mergeStandardTablesByKey(ownerUsername, fusionTaskId, targetTableName, standardTables, fusionConfig);
        }
        return mergeStandardTablesByAppend(ownerUsername, fusionTaskId, targetTableName, standardTables);
    }

    private int mergeStandardTablesByAppend(String ownerUsername, Long fusionTaskId, String targetTableName, List<String> standardTables) {
        String targetTableRef = stagingTableRef(targetTableName);
        int total = 0;
        for (String table : standardTables) {
            String safeStandardTable = sanitizeTableName(table);
            String sourceTableRef = stagingTableRef(safeStandardTable);
            Map<String, Object> cleanTask = dataProcessTaskRepository.findCleanTaskByStandardTable(ownerUsername, safeStandardTable);
            Long cleanTaskId = toLong(cleanTask.get("id"));
            if (cleanTaskId == null) {
                throw new IllegalArgumentException("清洗任务缺失: " + safeStandardTable);
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

    private int mergeStandardTablesByKey(
        String ownerUsername,
        Long fusionTaskId,
        String targetTableName,
        List<String> standardTables,
        Map<String, Object> fusionConfig
    ) {
        String targetTableRef = stagingTableRef(targetTableName);
        Map<String, List<String>> activeKeySynonyms = loadActiveKeySynonyms(ownerUsername);
        String configuredKey = text(fusionConfig.get("keyField"));
        String keyField = configuredKey;
        List<String> compositeKeyFields = parseKeyFields(configuredKey);
        if (isBlank(keyField)) {
            keyField = detectKeyField(standardTables, activeKeySynonyms);
        }
        if (isBlank(keyField)) {
            return mergeStandardTablesByAppend(ownerUsername, fusionTaskId, targetTableName, standardTables);
        }

        LinkedHashMap<String, Map<String, Object>> mergedByKey = new LinkedHashMap<>();
        AtomicInteger rowNo = new AtomicInteger(1);

        for (String table : standardTables) {
            String safeStandardTable = sanitizeTableName(table);
            String sourceTableRef = stagingTableRef(safeStandardTable);
            String tableTag = safeStandardTable;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT source_id, object_name, row_no, raw_json, normalized_json FROM " + sourceTableRef + " ORDER BY row_no ASC"
            );

            for (Map<String, Object> row : rows) {
                Map<String, Object> normalized = parseJsonObject(row.get("normalized_json"));
                Object keyValue = compositeKeyFields.size() > 1
                    ? buildCompositeKeyValue(normalized, compositeKeyFields, activeKeySynonyms)
                    : findFieldValue(normalized, keyField, activeKeySynonyms);
                if (keyValue == null || isBlank(String.valueOf(keyValue))) {
                    continue;
                }

                String key = String.valueOf(keyValue);
                Map<String, Object> bucket = mergedByKey.computeIfAbsent(key, k -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("rowNo", rowNo.getAndIncrement());
                    data.put("merged", new LinkedHashMap<String, Object>());
                    data.put("raw", new ArrayList<Map<String, Object>>());
                    data.put("sources", new LinkedHashSet<String>());
                    return data;
                });

                @SuppressWarnings("unchecked")
                Map<String, Object> merged = (Map<String, Object>) bucket.get("merged");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rawList = (List<Map<String, Object>>) bucket.get("raw");
                @SuppressWarnings("unchecked")
                Set<String> sources = (Set<String>) bucket.get("sources");

                if (compositeKeyFields.size() > 1) {
                    merged.put("_compositeKey", keyValue);
                    for (String fieldName : compositeKeyFields) {
                        Object part = findFieldValue(normalized, fieldName, activeKeySynonyms);
                        if (part != null) {
                            merged.put(fieldName, part);
                        }
                    }
                } else {
                    merged.put(keyField, keyValue);
                }
                for (Map.Entry<String, Object> entry : normalized.entrySet()) {
                    String field = entry.getKey();
                    if (field == null || field.isBlank()) {
                        continue;
                    }
                    if (field.equalsIgnoreCase(keyField)) {
                        continue;
                    }
                    merged.put(tableTag + "__" + field, entry.getValue());
                }

                Map<String, Object> rawItem = new LinkedHashMap<>();
                rawItem.put("table", tableTag);
                rawItem.put("sourceId", toLong(row.get("source_id")) == null ? 0L : toLong(row.get("source_id")));
                rawItem.put("objectName", text(row.get("object_name")));
                rawItem.put("rowNo", row.get("row_no"));
                rawItem.put("raw", parseJsonObject(row.get("raw_json")));
                rawList.add(rawItem);
                sources.add(tableTag);
            }
        }

        int inserted = 0;
        for (Map<String, Object> bucket : mergedByKey.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> merged = (Map<String, Object>) bucket.get("merged");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawList = (List<Map<String, Object>>) bucket.get("raw");
            @SuppressWarnings("unchecked")
            Set<String> sources = (Set<String>) bucket.get("sources");

            jdbcTemplate.update(
                "INSERT INTO " + targetTableRef + "(fusion_task_id,clean_task_id,source_id,object_name,row_no,raw_json,normalized_json,source_standard_table,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                fusionTaskId,
                0L,
                0L,
                "MERGED",
                bucket.get("rowNo"),
                toJson(rawList),
                toJson(merged),
                String.join(",", sources),
                now()
            );
            inserted++;
        }

        return inserted;
    }

    private String detectKeyField(List<String> standardTables, Map<String, List<String>> synonyms) {
        List<List<String>> keySets = new ArrayList<>();
        for (String table : standardTables) {
            String safeStandardTable = sanitizeTableName(table);
            String sourceTableRef = stagingTableRef(safeStandardTable);
            List<Map<String, Object>> sampleRows = jdbcTemplate.queryForList(
                "SELECT normalized_json FROM " + sourceTableRef + " LIMIT 1"
            );
            if (sampleRows.isEmpty()) {
                return "";
            }
            Map<String, Object> row = parseJsonObject(sampleRows.get(0).get("normalized_json"));
            keySets.add(new ArrayList<>(row.keySet()));
        }
        if (keySets.isEmpty()) {
            return "";
        }

        List<String> firstKeys = keySets.get(0);
        for (String candidate : firstKeys) {
            if (isBlank(candidate)) {
                continue;
            }
            boolean allMatched = true;
            for (int i = 1; i < keySets.size(); i++) {
                boolean matchedInTable = keySets.get(i).stream().anyMatch(field -> keyNameCompatible(field, candidate, synonyms));
                if (!matchedInTable) {
                    allMatched = false;
                    break;
                }
            }
            if (allMatched) {
                return candidate;
            }
        }
        return "";
    }

    private Object findFieldValue(Map<String, Object> row, String keyField, Map<String, List<String>> synonyms) {
        if (row.isEmpty() || isBlank(keyField)) {
            return null;
        }
        List<String> keyCandidates = expandKeyCandidates(keyField, synonyms);
        for (String candidate : keyCandidates) {
            if (row.containsKey(candidate)) {
                return row.get(candidate);
            }
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            for (String candidate : keyCandidates) {
                if (keyNameCompatible(entry.getKey(), candidate, synonyms)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private boolean keyNameCompatible(String field, String keyField, Map<String, List<String>> synonyms) {
        if (field == null || keyField == null) {
            return false;
        }
        String left = canonicalKeyName(field);
        String right = canonicalKeyName(keyField);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        if (synonyms.getOrDefault(left, List.of()).contains(right) || synonyms.getOrDefault(right, List.of()).contains(left)) {
            return true;
        }
        return left.endsWith(right);
    }

    private List<String> expandKeyCandidates(String keyField, Map<String, List<String>> synonyms) {
        String base = canonicalKeyName(keyField);
        if (base.isEmpty()) {
            return List.of();
        }
        List<String> aliases = synonyms.getOrDefault(base, List.of());
        List<String> out = new ArrayList<>();
        out.add(base);
        out.addAll(aliases);
        return out.stream().distinct().toList();
    }

    private String canonicalKeyName(String key) {
        if (key == null) {
            return "";
        }
        return key.trim().toLowerCase().replace("_", "").replace(" ", "");
    }

    private Map<String, List<String>> buildKeySynonyms(String keySynonymsJson) {
        Map<String, List<String>> map = new HashMap<>();
        mergeSynonymGroup(map, List.of("整改单位ID", "单位ID", "dept_id", "organization_id"));
        mergeSynonymGroup(map, List.of("整改事项ID", "事项ID", "issue_id", "rect_id"));
        mergeSynonymGroup(map, List.of("问题类别", "类别", "category", "problem_category"));
        mergeSynonymGroup(map, List.of("统计日期", "日期", "date", "stat_date"));

        if (!isBlank(keySynonymsJson)) {
            try {
                Map<String, List<String>> custom = objectMapper.readValue(keySynonymsJson, new TypeReference<>() {});
                for (Map.Entry<String, List<String>> entry : custom.entrySet()) {
                    String normalizedKey = canonicalizeStatic(entry.getKey());
                    if (normalizedKey.isEmpty()) {
                        continue;
                    }
                    List<String> aliases = entry.getValue() == null
                        ? List.of()
                        : entry.getValue().stream().map(StagingTableService::canonicalizeStatic).filter(it -> !it.isEmpty()).toList();
                    List<String> group = new ArrayList<>();
                    group.add(normalizedKey);
                    group.addAll(aliases);
                    mergeSynonymGroup(map, group);
                }
            } catch (Exception ex) {
                log.warn("Invalid APP_FUSION_KEY_SYNONYMS_JSON, fallback to default synonyms only", ex);
            }
        }

        return Collections.unmodifiableMap(map);
    }

    private static void mergeSynonymGroup(Map<String, List<String>> map, List<String> keys) {
        List<String> canonicalized = keys.stream()
            .map(StagingTableService::canonicalizeStatic)
            .filter(it -> !it.isEmpty())
            .distinct()
            .toList();
        for (String key : canonicalized) {
            LinkedHashSet<String> aliases = new LinkedHashSet<>(map.getOrDefault(key, List.of()));
            for (String alias : canonicalized) {
                if (!alias.equals(key)) {
                    aliases.add(alias);
                }
            }
            map.put(key, new ArrayList<>(aliases));
        }
    }

    private Map<String, List<String>> loadActiveKeySynonyms(String ownerUsername) {
        Map<String, List<String>> merged = new HashMap<>();
        defaultKeySynonyms.forEach((key, value) -> merged.put(key, new ArrayList<>(value)));
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT canonical_key, aliases_json FROM fusion_key_synonym_record WHERE owner_username=? AND enabled=1",
                ownerUsername
            );
            for (Map<String, Object> row : rows) {
                String canonical = canonicalizeStatic(String.valueOf(row.get("canonical_key")));
                if (canonical.isEmpty()) {
                    continue;
                }
                List<String> aliases = parseAliasesJson(String.valueOf(row.get("aliases_json")));
                List<String> group = new ArrayList<>();
                group.add(canonical);
                group.addAll(aliases);
                mergeSynonymGroup(merged, group);
            }
        } catch (Exception ex) {
            log.warn("Failed loading key synonyms from DB, fallback to defaults only", ex);
        }
        return Collections.unmodifiableMap(merged);
    }

    private List<String> parseAliasesJson(String aliasesJson) {
        if (isBlank(aliasesJson)) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(aliasesJson, new TypeReference<>() {});
            return parsed.stream().map(StagingTableService::canonicalizeStatic).filter(it -> !it.isEmpty()).toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String canonicalizeStatic(String key) {
        if (key == null) {
            return "";
        }
        return key.trim().toLowerCase().replace("_", "").replace(" ", "");
    }

    private List<String> parseKeyFields(String expression) {
        String text = text(expression);
        if (isBlank(text)) {
            return List.of();
        }
        String normalized = text.replace("＋", "+");
        if (!(normalized.contains("+") || normalized.contains(",") || normalized.contains("|"))) {
            return List.of(text);
        }
        String[] parts = normalized.split("[+,|]");
        List<String> fields = new ArrayList<>();
        for (String part : parts) {
            String item = part == null ? "" : part.trim();
            if (!item.isEmpty()) {
                fields.add(item);
            }
        }
        return fields;
    }

    private String buildCompositeKeyValue(Map<String, Object> row, List<String> keyFields, Map<String, List<String>> synonyms) {
        if (row.isEmpty() || keyFields.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (String keyField : keyFields) {
            Object value = findFieldValue(row, keyField, synonyms);
            if (value == null || isBlank(String.valueOf(value))) {
                return "";
            }
            values.add(String.valueOf(value).trim());
        }
        return String.join("|", values);
    }

    private Map<String, Object> parseJsonObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        String json = text(value);
        if (isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public Map<String, Object> persistCleanResultToLayers(String ownerUsername, Long cleanTaskId, String standardTable) {
        String tableRef = stagingTableRef(standardTable);
        String tenantId = resolveTenantId(ownerUsername);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT source_id, object_name, row_no, raw_json, normalized_json FROM " + tableRef + " WHERE task_id=? ORDER BY row_no ASC",
            cleanTaskId
        );

        int bronzeRows = 0;
        int silverRows = 0;
        for (Map<String, Object> row : rows) {
            Long sourceId = toLong(row.get("source_id"));
            String objectName = text(row.get("object_name"));
            Integer rowNo = row.get("row_no") instanceof Number n ? n.intValue() : null;

            jdbcTemplate.update(
                "INSERT INTO bronze_ingest_record(tenant_id,owner_username,ingest_type,source_task_type,source_task_id,source_table,source_object,row_no,raw_payload_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                tenantId,
                ownerUsername,
                "CLEAN_IMPORT",
                "CLEAN",
                cleanTaskId,
                standardTable,
                objectName,
                rowNo,
                text(row.get("raw_json")),
                now()
            );
            bronzeRows++;

            jdbcTemplate.update(
                "INSERT INTO silver_standard_record(tenant_id,owner_username,standard_table,source_task_type,source_task_id,source_id,source_object,row_no,normalized_payload_json,quality_status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                tenantId,
                ownerUsername,
                standardTable,
                "CLEAN",
                cleanTaskId,
                sourceId,
                objectName,
                rowNo,
                text(row.get("normalized_json")),
                "PASSED",
                now()
            );
            silverRows++;
        }

        return Map.of(
            "bronzeRows", bronzeRows,
            "silverRows", silverRows,
            "standardTable", standardTable,
            "taskId", cleanTaskId
        );
    }

    public Map<String, Object> persistFusionResultToGold(String ownerUsername, Long fusionTaskId, String targetTable, String strategy) {
        String tableRef = stagingTableRef(targetTable);
        String tenantId = resolveTenantId(ownerUsername);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT row_no, raw_json, normalized_json, source_standard_table FROM " + tableRef + " WHERE fusion_task_id=? ORDER BY row_no ASC",
            fusionTaskId
        );

        int goldRows = 0;
        for (Map<String, Object> row : rows) {
            Integer rowNo = row.get("row_no") instanceof Number n ? n.intValue() : 0;
            Map<String, Object> normalized = parseJsonObject(row.get("normalized_json"));
            Object key = normalized.get("_compositeKey");
            if (key == null || isBlank(String.valueOf(key))) {
                key = normalized.get("id");
            }
            String entityKey = (key == null || isBlank(String.valueOf(key)))
                ? targetTable + "#" + rowNo
                : String.valueOf(key);

            String sourceStandardTable = text(row.get("source_standard_table"));
            List<String> sourceTables = isBlank(sourceStandardTable)
                ? List.of()
                : List.of(sourceStandardTable.split(","));
            double confidence = sourceTables.size() > 1 ? 0.95D : 0.85D;

            jdbcTemplate.update(
                "INSERT INTO gold_fusion_wide_record(tenant_id,owner_username,gold_table,fusion_task_id,entity_key,match_type,confidence,source_records_json,merged_payload_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                tenantId,
                ownerUsername,
                targetTable,
                fusionTaskId,
                entityKey,
                isBlank(strategy) ? "KEY_ALIGN" : strategy.toUpperCase(),
                confidence,
                text(row.get("raw_json")),
                text(row.get("normalized_json")),
                now()
            );
            goldRows++;
        }

        return Map.of(
            "goldRows", goldRows,
            "targetTable", targetTable,
            "taskId", fusionTaskId
        );
    }

    private String resolveTenantId(String ownerUsername) {
        String normalized = text(ownerUsername);
        int pos = normalized.indexOf(':');
        if (pos > 0) {
            return normalized.substring(0, pos);
        }
        return "default";
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

    private List<String> extractRows(String ownerUsername, Long sourceId, String objectName) {
        Map<String, Object> source = getSourceById(ownerUsername, sourceId);
        String sourceType = text(source.get("type")).toUpperCase();
        return switch (sourceType) {
            case "DATABASE" -> readDatabaseRows(objectName);
            case "FILE" -> fileRowReader.readRows(text(source.get("filePath")), text(source.get("fileName")));
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + sourceType);
        };
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
            throw new IllegalArgumentException("数据源不存在: " + sourceId);
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
            throw new IllegalArgumentException("schema 名不合法: " + schemaName);
        }
        return normalized;
    }

    private String sanitizeTableName(String tableName) {
        String normalized = text(tableName);
        if (!SAFE_TABLE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("表名不合法: " + tableName);
        }
        return normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON序列化失败");
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




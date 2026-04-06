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
    private final ObjectMapper objectMapper;
    private final String stagingSchema;
    private final Map<String, List<String>> defaultKeySynonyms;

    public StagingTableService(
        JdbcTemplate jdbcTemplate,
        DataProcessTaskRepository dataProcessTaskRepository,
        ObjectMapper objectMapper,
        @Value("${app.datasource.staging-schema:agent_audit_staging}") String stagingSchema,
        @Value("${app.fusion.key-synonyms-json:}") String keySynonymsJson
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataProcessTaskRepository = dataProcessTaskRepository;
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

    private Map<String, Object> buildEffectiveFusionConfig(String strategy, Map<String, Object> fusionConfig) {
        Map<String, Object> effective = fusionConfig == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fusionConfig);
        String configuredKey = text(effective.get("keyField"));
        if (isBlank(configuredKey)) {
            configuredKey = text(effective.get("businessKey"));
            if (!isBlank(configuredKey)) {
                effective.put("keyField", configuredKey);
            }
        }
        if (!isBlank(configuredKey)) {
            return effective;
        }

        if ("RULE_MATCH".equals(strategy)) {
            List<String> matchFields = castStringList(effective.get("matchFields"));
            if (!matchFields.isEmpty()) {
                effective.put("keyField", String.join("+", matchFields));
            }
            return effective;
        }

        if ("TIME_WINDOW".equals(strategy)) {
            String timeField = text(effective.get("timeField"));
            if (!isBlank(timeField)) {
                effective.put("keyField", timeField);
            }
        }
        return effective;
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
            compositeKeyFields = parseKeyFields(keyField);
        }
        if (isBlank(keyField)) {
            return mergeStandardTablesByAppend(ownerUsername, fusionTaskId, targetTableName, standardTables);
        }

        Object looseFlag = fusionConfig.get("loosePrimaryFallback");
        boolean loosePrimaryFallback = compositeKeyFields.size() > 1
            && (Boolean.TRUE.equals(looseFlag) || "true".equalsIgnoreCase(text(looseFlag)));
        Object fillMissingFlag = fusionConfig.get("fillMissingSourceRows");
        boolean fillMissingSourceRows = Boolean.TRUE.equals(fillMissingFlag)
            || "true".equalsIgnoreCase(text(fillMissingFlag));
        String primaryKeyField = compositeKeyFields.isEmpty() ? keyField : compositeKeyFields.get(0);

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
                Long sourceIdValue = toLong(row.get("source_id"));
                long safeSourceId = sourceIdValue == null ? 0L : sourceIdValue;
                int safeRowNo = row.get("row_no") instanceof Number n ? n.intValue() : 0;

                String key = keyValue == null ? "" : String.valueOf(keyValue).trim();
                String matchKey = normalizeFusionMatchKey(key);
                if (isBlank(matchKey)) {
                    // Try fallback signature match for rows without primary key before downgrading to singleton.
                    String fallbackMatchKey = buildFallbackMatchKey(normalized, fusionConfig, activeKeySynonyms);
                    if (!isBlank(fallbackMatchKey)) {
                        matchKey = "__fallback__" + fallbackMatchKey;
                    } else {
                        // Keep rows without key instead of dropping them to avoid data loss in fusion result.
                        matchKey = "__row__" + tableTag + "#" + safeSourceId + "#" + safeRowNo;
                    }
                } else {
                    matchKey = "__key__" + matchKey;
                }

                Map<String, Object> bucket = mergedByKey.computeIfAbsent(matchKey, k -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("entriesByTable", new LinkedHashMap<String, List<Map<String, Object>>>());
                    data.put("compositeKeyValue", "");
                    data.put("singleKeyValue", "");
                    data.put("compositeParts", new LinkedHashMap<String, Object>());
                    data.put("primaryKeyNormalized", "");
                    data.put("primaryKeyValue", "");
                    data.put("fallbackBucket", Boolean.FALSE);
                    return data;
                });

                @SuppressWarnings("unchecked")
                Map<String, List<Map<String, Object>>> entriesByTable = (Map<String, List<Map<String, Object>>>) bucket.get("entriesByTable");
                List<Map<String, Object>> tableEntries = entriesByTable.computeIfAbsent(tableTag, k -> new ArrayList<>());

                Object primaryValue = findFieldValue(normalized, primaryKeyField, activeKeySynonyms);
                String primaryNormalized = normalizeFusionMatchKey(primaryValue);
                if (!isBlank(primaryNormalized)) {
                    Object existingPrimaryNorm = bucket.get("primaryKeyNormalized");
                    if (existingPrimaryNorm == null || isBlank(String.valueOf(existingPrimaryNorm))) {
                        bucket.put("primaryKeyNormalized", primaryNormalized);
                    }
                    Object existingPrimaryValue = bucket.get("primaryKeyValue");
                    if (existingPrimaryValue == null || isBlank(String.valueOf(existingPrimaryValue))) {
                        bucket.put("primaryKeyValue", primaryValue);
                    }
                }

                Map<String, Object> rawItem = new LinkedHashMap<>();
                rawItem.put("table", tableTag);
                rawItem.put("sourceId", safeSourceId);
                rawItem.put("objectName", text(row.get("object_name")));
                rawItem.put("rowNo", row.get("row_no"));
                rawItem.put("raw", parseJsonObject(row.get("raw_json")));
                rawItem.put("normalized", normalized);
                tableEntries.add(rawItem);

                if (compositeKeyFields.size() > 1) {
                    if (!isBlank(key)) {
                        Object existingComposite = bucket.get("compositeKeyValue");
                        if (existingComposite == null || isBlank(String.valueOf(existingComposite))) {
                            bucket.put("compositeKeyValue", keyValue);
                        }
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> compositeParts = (Map<String, Object>) bucket.get("compositeParts");
                    for (String fieldName : compositeKeyFields) {
                        if (compositeParts.containsKey(fieldName)) {
                            continue;
                        }
                        Object part = findFieldValue(normalized, fieldName, activeKeySynonyms);
                        if (part != null && !isBlank(String.valueOf(part))) {
                            compositeParts.put(fieldName, part);
                        }
                    }
                } else {
                    if (!isBlank(key)) {
                        Object existingSingle = bucket.get("singleKeyValue");
                        if (existingSingle == null || isBlank(String.valueOf(existingSingle))) {
                            bucket.put("singleKeyValue", keyValue);
                        }
                    }
                }
            }
        }

        List<Map<String, Object>> effectiveBuckets = buildEffectiveBucketsForLooseFallback(mergedByKey, loosePrimaryFallback);

        int inserted = 0;
        for (Map<String, Object> bucket : effectiveBuckets) {
            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, Object>>> entriesByTable = (Map<String, List<Map<String, Object>>>) bucket.get("entriesByTable");
            int maxRowsPerBucket = entriesByTable.values().stream().mapToInt(List::size).max().orElse(0);
            if (maxRowsPerBucket <= 0) {
                continue;
            }

            for (int i = 0; i < maxRowsPerBucket; i++) {
                Map<String, Object> merged = new LinkedHashMap<>();
                List<Map<String, Object>> rawList = new ArrayList<>();
                Set<String> sources = new LinkedHashSet<>();
                boolean fallbackBucket = Boolean.TRUE.equals(bucket.get("fallbackBucket"));

                if (compositeKeyFields.size() > 1 && !fallbackBucket) {
                    Object composite = bucket.get("compositeKeyValue");
                    if (composite != null && !isBlank(String.valueOf(composite))) {
                        merged.put("_compositeKey", composite);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> compositeParts = (Map<String, Object>) bucket.get("compositeParts");
                    for (String fieldName : compositeKeyFields) {
                        Object part = compositeParts.get(fieldName);
                        if (part != null) {
                            merged.put(fieldName, part);
                        }
                    }
                } else if (compositeKeyFields.size() > 1) {
                    Object primaryValue = bucket.get("primaryKeyValue");
                    if (primaryValue != null && !isBlank(String.valueOf(primaryValue))) {
                        merged.put(primaryKeyField, primaryValue);
                    }
                } else {
                    Object single = bucket.get("singleKeyValue");
                    if (single != null && !isBlank(String.valueOf(single))) {
                        merged.put(keyField, single);
                    }
                }

                for (Map.Entry<String, List<Map<String, Object>>> tableEntry : entriesByTable.entrySet()) {
                    List<Map<String, Object>> records = tableEntry.getValue();
                    if (i >= records.size()) {
                        continue;
                    }
                    Map<String, Object> record = records.get(i);
                    String tableTag = text(record.get("table"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> normalized = (Map<String, Object>) record.get("normalized");
                    for (Map.Entry<String, Object> fieldEntry : normalized.entrySet()) {
                        String field = fieldEntry.getKey();
                        if (field == null || field.isBlank()) {
                            continue;
                        }
                        if (field.equalsIgnoreCase(keyField)) {
                            continue;
                        }
                        merged.put(tableTag + "__" + field, fieldEntry.getValue());
                    }

                    Map<String, Object> rawItem = new LinkedHashMap<>();
                    rawItem.put("table", tableTag);
                    rawItem.put("sourceId", record.get("sourceId"));
                    rawItem.put("objectName", record.get("objectName"));
                    rawItem.put("rowNo", record.get("rowNo"));
                    rawItem.put("raw", record.get("raw"));
                    rawList.add(rawItem);
                    sources.add(tableTag);
                }

                if (fillMissingSourceRows) {
                    for (String expectedTable : standardTables) {
                        if (sources.contains(expectedTable)) {
                            continue;
                        }
                        Map<String, Object> placeholderRaw = new LinkedHashMap<>();
                        placeholderRaw.put("_missing", true);
                        placeholderRaw.put("_reason", "NO_MATCHED_SOURCE_ROW");
                        placeholderRaw.put("_sourceTable", expectedTable);

                        Map<String, Object> rawItem = new LinkedHashMap<>();
                        rawItem.put("table", expectedTable);
                        rawItem.put("sourceId", 0L);
                        rawItem.put("objectName", "MISSING_PLACEHOLDER");
                        rawItem.put("rowNo", null);
                        rawItem.put("raw", placeholderRaw);
                        rawList.add(rawItem);
                        sources.add(expectedTable);
                    }
                }

                if (rawList.isEmpty()) {
                    continue;
                }

                jdbcTemplate.update(
                    "INSERT INTO " + targetTableRef + "(fusion_task_id,clean_task_id,source_id,object_name,row_no,raw_json,normalized_json,source_standard_table,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    fusionTaskId,
                    0L,
                    0L,
                    "MERGED",
                    rowNo.getAndIncrement(),
                    toJson(rawList),
                    toJson(merged),
                    String.join(",", sources),
                    now()
                );
                inserted++;
            }
        }

        return inserted;
    }

    private List<Map<String, Object>> buildEffectiveBucketsForLooseFallback(
        LinkedHashMap<String, Map<String, Object>> strictBuckets,
        boolean loosePrimaryFallback
    ) {
        if (!loosePrimaryFallback || strictBuckets.isEmpty()) {
            return new ArrayList<>(strictBuckets.values());
        }

        List<Map<String, Object>> passthrough = new ArrayList<>();
        Map<String, List<Map<String, Object>>> fallbackGroups = new LinkedHashMap<>();

        for (Map<String, Object> bucket : strictBuckets.values()) {
            String primary = text(bucket.get("primaryKeyNormalized"));
            if (!isBlank(primary)) {
                fallbackGroups.computeIfAbsent(primary, k -> new ArrayList<>()).add(bucket);
            } else {
                passthrough.add(bucket);
            }
        }

        for (List<Map<String, Object>> group : fallbackGroups.values()) {
            Set<String> tableSet = new LinkedHashSet<>();
            for (Map<String, Object> bucket : group) {
                @SuppressWarnings("unchecked")
                Map<String, List<Map<String, Object>>> entriesByTable = (Map<String, List<Map<String, Object>>>) bucket.get("entriesByTable");
                tableSet.addAll(entriesByTable.keySet());
            }

            if (tableSet.size() <= 1 || group.size() <= 1) {
                passthrough.addAll(group);
                continue;
            }

            Map<String, Object> combined = new LinkedHashMap<>();
            Map<String, List<Map<String, Object>>> mergedEntries = new LinkedHashMap<>();
            Map<String, Object> mergedParts = new LinkedHashMap<>();
            Object primaryValue = "";
            String primaryNormalized = "";
            for (Map<String, Object> bucket : group) {
                @SuppressWarnings("unchecked")
                Map<String, List<Map<String, Object>>> entriesByTable = (Map<String, List<Map<String, Object>>>) bucket.get("entriesByTable");
                for (Map.Entry<String, List<Map<String, Object>>> entry : entriesByTable.entrySet()) {
                    mergedEntries.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> parts = (Map<String, Object>) bucket.get("compositeParts");
                for (Map.Entry<String, Object> entry : parts.entrySet()) {
                    mergedParts.putIfAbsent(entry.getKey(), entry.getValue());
                }

                if (isBlank(String.valueOf(primaryValue))) {
                    Object candidate = bucket.get("primaryKeyValue");
                    if (candidate != null && !isBlank(String.valueOf(candidate))) {
                        primaryValue = candidate;
                    }
                }
                if (isBlank(primaryNormalized)) {
                    String candidate = text(bucket.get("primaryKeyNormalized"));
                    if (!isBlank(candidate)) {
                        primaryNormalized = candidate;
                    }
                }
            }

            combined.put("entriesByTable", mergedEntries);
            combined.put("compositeKeyValue", "");
            combined.put("singleKeyValue", primaryValue);
            combined.put("compositeParts", mergedParts);
            combined.put("primaryKeyNormalized", primaryNormalized);
            combined.put("primaryKeyValue", primaryValue);
            combined.put("fallbackBucket", Boolean.TRUE);
            passthrough.add(combined);
        }

        return passthrough;
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
        List<String> sharedCandidates = firstKeys.stream()
            .filter(candidate -> {
                if (isBlank(candidate)) {
                    return false;
                }
                for (int i = 1; i < keySets.size(); i++) {
                    boolean matchedInTable = keySets.get(i).stream().anyMatch(field -> keyNameCompatible(field, candidate, synonyms));
                    if (!matchedInTable) {
                        return false;
                    }
                }
                return true;
            })
            .toList();

        String idLikeCandidate = sharedCandidates.stream()
            .filter(this::isIdentifierLikeField)
            .findFirst()
            .orElse("");
        String semanticCandidate = sharedCandidates.stream()
            .filter(candidate -> !keyNameCompatible(candidate, idLikeCandidate, synonyms))
            .filter(this::isSemanticPartitionField)
            .findFirst()
            .orElse("");
        if (!isBlank(idLikeCandidate) && !isBlank(semanticCandidate)) {
            return idLikeCandidate + "+" + semanticCandidate;
        }

        // Prefer stable identifier-like keys (e.g. customer_id, issue_id, xxxcode) to avoid matching on name/date fields.
        List<String> preferred = sharedCandidates.stream()
            .filter(this::isIdentifierLikeField)
            .toList();

        for (String candidate : preferred) {
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

        for (String candidate : sharedCandidates) {
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

    private boolean isIdentifierLikeField(String fieldName) {
        String normalized = canonicalKeyName(fieldName);
        return normalized.contains("id")
            || normalized.endsWith("code")
            || normalized.contains("no")
            || normalized.contains("number");
    }

    private boolean isSemanticPartitionField(String fieldName) {
        String normalized = canonicalKeyName(fieldName);
        return normalized.contains("category")
            || normalized.contains("type")
            || normalized.contains("status")
            || normalized.contains("date")
            || normalized.contains("类别")
            || normalized.contains("类型")
            || normalized.contains("状态")
            || normalized.contains("日期");
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

    private String normalizeFusionMatchKey(Object keyValue) {
        if (keyValue == null) {
            return "";
        }
        String raw = String.valueOf(keyValue).trim();
        if (raw.isEmpty()) {
            return "";
        }
        String compact = raw.replaceAll("\\s+", "");
        return compact.toLowerCase();
    }

    private String buildFallbackMatchKey(Map<String, Object> row, Map<String, Object> fusionConfig, Map<String, List<String>> synonyms) {
        if (row == null || row.isEmpty()) {
            return "";
        }

        // Strong identifiers are preferred to avoid accidental over-merge.
        List<String> strongFields = List.of("email", "mail", "phone", "mobile", "手机号", "邮箱");
        for (String field : strongFields) {
            Object value = findFieldValue(row, field, synonyms);
            String normalized = normalizeFusionMatchKey(value);
            if (!isBlank(normalized)) {
                return field + ":" + normalized;
            }
        }

        List<String> configuredFields = castStringList(fusionConfig.get("secondaryMatchFields"));
        List<String> fallbackFields = configuredFields.isEmpty()
            ? List.of("full_name", "name", "province", "city")
            : configuredFields;

        List<String> parts = new ArrayList<>();
        for (String field : fallbackFields) {
            Object value = findFieldValue(row, field, synonyms);
            String normalized = normalizeFusionMatchKey(value);
            if (!isBlank(normalized)) {
                parts.add(field + ":" + normalized);
            }
        }

        if (parts.size() >= 2) {
            return String.join("|", parts);
        }
        return "";
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

    private List<String> castStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String text = text(item);
            if (!text.isBlank()) {
                out.add(text);
            }
        }
        return out;
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




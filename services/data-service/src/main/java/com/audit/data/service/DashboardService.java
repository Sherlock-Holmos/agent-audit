package com.audit.data.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.audit.data.service.cache.RedisCacheService;
import java.time.Duration;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
/**
 * 仪表板聚合服务：负责融合结果的统计、趋势和热力图计算，并结合短 TTL 缓存减少重复查询。
 */
public class DashboardService implements IDashboardService {

    private static final String DASHBOARD_CACHE_PREFIX = "dashboard:";
    private static final Duration DASHBOARD_TTL = Duration.ofSeconds(60);
    private static final Duration TREND_TTL = Duration.ofSeconds(90);
    private static final Duration HEATMAP_TTL = Duration.ofSeconds(120);
    private static final Duration FUSION_OPTIONS_TTL = Duration.ofSeconds(45);
    private static final Pattern SAFE_TABLE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");
    private static final Pattern SAFE_SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final List<String> GROUP_KEYS = List.of(
        "department", "dept", "province", "city", "channel", "status", "region", "type"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisCacheService redisCacheService;
    private final String stagingSchema;

    public DashboardService(
        JdbcTemplate jdbcTemplate,
        RedisCacheService redisCacheService,
        @Value("${app.datasource.staging-schema:agent_audit_staging}") String stagingSchema
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisCacheService = redisCacheService;
        this.stagingSchema = sanitizeSchemaName(stagingSchema);
    }

    public List<Map<String, Object>> listFusionOptions(String ownerUsername) {
        String key = cacheKey(ownerUsername, "fusion-options");
        return redisCacheService.getOrCompute(
            key,
            FUSION_OPTIONS_TTL,
            new TypeReference<List<Map<String, Object>>>() {},
            () -> listFusionOptionsInternal(ownerUsername)
        );
    }

    private List<Map<String, Object>> listFusionOptionsInternal(String ownerUsername) {
        return jdbcTemplate.query(
            """
            SELECT id, task_name, target_table, fusion_rows, updated_at
              FROM fusion_task_record
             WHERE owner_username=? AND status='COMPLETED'
             ORDER BY id DESC
            """,
            (rs, i) -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", String.valueOf(rs.getLong("id")));
                item.put("taskName", rs.getString("task_name"));
                item.put("targetTable", rs.getString("target_table"));
                item.put("fusionRows", rs.getInt("fusion_rows"));
                item.put("updatedAt", rs.getTimestamp("updated_at") == null ? "" : rs.getTimestamp("updated_at").toString());
                return item;
            },
            ownerUsername
        );
    }

    public Map<String, Object> buildDashboard(String ownerUsername, Long fusionTaskId) {
        String key = cacheKey(ownerUsername, "dashboard:" + fusionKeySuffix(fusionTaskId));
        return redisCacheService.getOrCompute(
            key,
            DASHBOARD_TTL,
            new TypeReference<Map<String, Object>>() {},
            () -> buildDashboardInternal(ownerUsername, fusionTaskId)
        );
    }

    private Map<String, Object> buildDashboardInternal(String ownerUsername, Long fusionTaskId) {
        TargetTableInfo target = resolveTargetTable(ownerUsername, fusionTaskId);
        if (target == null) {
            return Map.of(
                "fusionTaskId", "",
                "targetTable", "",
                "completedRate", 0,
                "overdueCount", 0,
                "departmentRank", 0,
                "totalRows", 0,
                "message", "鏆傛棤鍙垎鏋愮殑铻嶅悎缁撴灉"
            );
        }

        String tableName = sanitizeTableName(target.targetTable);
        String tableRef = stagingTableRef(tableName);
        if (!tableExists(tableName)) {
            return Map.of(
                "fusionTaskId", String.valueOf(target.fusionTaskId),
                "targetTable", tableName,
                "completedRate", 0,
                "overdueCount", 0,
                "departmentRank", 0,
                "totalRows", 0,
                "message", "铻嶅悎缁撴灉琛ㄤ笉瀛樺湪锛岃閲嶆柊鎵ц铻嶅悎浠诲姟"
            );
        }

        Integer totalRows = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + tableRef, Integer.class);
        int total = totalRows == null ? 0 : totalRows;
        if (total == 0) {
            return Map.of(
                "fusionTaskId", String.valueOf(target.fusionTaskId),
                "targetTable", tableName,
                "completedRate", 0,
                "overdueCount", 0,
                "departmentRank", 0,
                "totalRows", 0,
                "message", "铻嶅悎缁撴灉涓虹┖"
            );
        }

        Integer unknownRows = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM " + tableRef + " WHERE normalized_json LIKE '%\\\"UNKNOWN\\\"%'",
            Integer.class
        );
        int unknown = unknownRows == null ? 0 : unknownRows;
        int valid = Math.max(total - unknown, 0);
        int completedRate = percent(valid, total);

        int departmentRank;
        List<Integer> allRows = jdbcTemplate.query(
            "SELECT fusion_rows FROM fusion_task_record WHERE owner_username=? AND status='COMPLETED'",
            (rs, i) -> rs.getInt("fusion_rows"),
            ownerUsername
        );
        allRows.sort(Comparator.reverseOrder());
        int currentRows = Math.max(target.fusionRows, total);
        int idx = allRows.indexOf(currentRows);
        departmentRank = idx >= 0 ? idx + 1 : allRows.size() + 1;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fusionTaskId", String.valueOf(target.fusionTaskId));
        result.put("targetTable", tableName);
        result.put("completedRate", completedRate);
        result.put("overdueCount", unknown);
        result.put("departmentRank", departmentRank);
        result.put("totalRows", total);
        return result;
    }

    public Map<String, Object> buildTrend(String ownerUsername, Long fusionTaskId) {
        String key = cacheKey(ownerUsername, "trend:" + fusionKeySuffix(fusionTaskId));
        return redisCacheService.getOrCompute(
            key,
            TREND_TTL,
            new TypeReference<Map<String, Object>>() {},
            () -> buildTrendInternal(ownerUsername, fusionTaskId)
        );
    }

    private Map<String, Object> buildTrendInternal(String ownerUsername, Long fusionTaskId) {
        TargetTableInfo target = resolveTargetTable(ownerUsername, fusionTaskId);
        if (target == null || !tableExists(sanitizeTableName(target.targetTable))) {
            return emptyTrend();
        }

        String tableName = sanitizeTableName(target.targetTable);
        String tableRef = stagingTableRef(tableName);
        String trendSql = Objects.requireNonNull(
            """
             SELECT DATE_FORMAT(created_at, '%%m-%%d') AS day_label,
                   COUNT(1) AS total_count,
                 SUM(CASE WHEN normalized_json LIKE '%%\\"UNKNOWN\\"%%' THEN 1 ELSE 0 END) AS unknown_count
             FROM %s
             GROUP BY DATE(created_at), DATE_FORMAT(created_at, '%%m-%%d')
             ORDER BY DATE(created_at) DESC
             LIMIT 7
            """.formatted(tableRef)
        );
        List<Map<String, Object>> rows = jdbcTemplate.query(
            trendSql,
            (rs, i) -> {
                Map<String, Object> item = new HashMap<>();
                item.put("day", rs.getString("day_label"));
                item.put("total", rs.getInt("total_count"));
                item.put("unknown", rs.getInt("unknown_count"));
                return item;
            }
        );

        if (rows.isEmpty()) {
            return emptyTrend();
        }

        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("day"))));
        List<String> dates = new ArrayList<>();
        List<Integer> rates = new ArrayList<>();
        List<Integer> predicted = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int total = ((Number) row.get("total")).intValue();
            int unknown = ((Number) row.get("unknown")).intValue();
            int done = Math.max(total - unknown, 0);
            int rate = percent(done, total);
            dates.add(String.valueOf(row.get("day")));
            rates.add(rate);
        }

        for (int i = 0; i < rates.size(); i++) {
            int base = rates.get(i);
            int next = i == rates.size() - 1 ? base : rates.get(i + 1);
            predicted.add((int) Math.min(100, Math.round((base * 0.6 + next * 0.4 + 2))));
        }

        return Map.of(
            "fusionTaskId", String.valueOf(target.fusionTaskId),
            "targetTable", tableName,
            "dates", dates,
            "rates", rates,
            "predicted", predicted
        );
    }

    public Map<String, Object> buildHeatmap(String ownerUsername, Long fusionTaskId) {
        String key = cacheKey(ownerUsername, "heatmap:" + fusionKeySuffix(fusionTaskId));
        return redisCacheService.getOrCompute(
            key,
            HEATMAP_TTL,
            new TypeReference<Map<String, Object>>() {},
            () -> buildHeatmapInternal(ownerUsername, fusionTaskId)
        );
    }

    private Map<String, Object> buildHeatmapInternal(String ownerUsername, Long fusionTaskId) {
        TargetTableInfo target = resolveTargetTable(ownerUsername, fusionTaskId);
        if (target == null || !tableExists(sanitizeTableName(target.targetTable))) {
            return Map.of("departments", List.of(), "metrics", List.of(), "values", List.of());
        }

        String tableName = sanitizeTableName(target.targetTable);
        String tableRef = stagingTableRef(tableName);
        List<Map<String, Object>> sourceRows = jdbcTemplate.query(
            "SELECT object_name, normalized_json FROM " + tableRef + " LIMIT 50000",
            (rs, i) -> {
                Map<String, Object> item = new HashMap<>();
                item.put("objectName", rs.getString("object_name"));
                item.put("normalizedJson", rs.getString("normalized_json"));
                return item;
            }
        );

        if (sourceRows.isEmpty()) {
            return Map.of("departments", List.of(), "metrics", List.of(), "values", List.of());
        }

        Map<String, GroupStats> grouped = new HashMap<>();
        for (Map<String, Object> row : sourceRows) {
            String objectName = String.valueOf(row.get("objectName"));
            String normalizedJson = String.valueOf(row.get("normalizedJson"));
            Map<String, Object> payload = parseJsonObject(normalizedJson);
            String groupLabel = chooseGroupLabel(payload, objectName);

            GroupStats stats = grouped.computeIfAbsent(groupLabel, key -> new GroupStats());
            stats.total += 1;
            if (normalizedJson.contains("\\\"UNKNOWN\\\"")) {
                stats.unknown += 1;
            }
            stats.distinctPayload.add(normalizedJson);
        }

        List<Map.Entry<String, GroupStats>> rows = grouped.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().total, a.getValue().total))
            .limit(8)
            .toList();

        List<String> departments = new ArrayList<>();
        List<String> metrics = List.of("瀹屾垚鐜", "绌哄€肩巼", "閲嶅鐜", "闂幆鐜");
        List<List<Integer>> values = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map.Entry<String, GroupStats> row = rows.get(i);
            GroupStats stats = row.getValue();
            departments.add(row.getKey());
            int total = stats.total;
            int unknown = stats.unknown;
            int distinct = stats.distinctPayload.size();

            int completion = percent(Math.max(total - unknown, 0), total);
            int unknownRate = percent(unknown, total);
            int duplicateRate = total == 0 ? 0 : percent(Math.max(total - distinct, 0), total);
            int closureRate = Math.max(Math.min(completion - duplicateRate / 2, 100), 0);

            values.add(List.of(i, 0, completion));
            values.add(List.of(i, 1, unknownRate));
            values.add(List.of(i, 2, duplicateRate));
            values.add(List.of(i, 3, closureRate));
        }

        return Map.of(
            "fusionTaskId", String.valueOf(target.fusionTaskId),
            "targetTable", tableName,
            "departments", departments,
            "metrics", metrics,
            "values", values
        );
    }

    private Map<String, Object> parseJsonObject(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String chooseGroupLabel(Map<String, Object> payload, String fallback) {
        for (String key : GROUP_KEYS) {
            String value = findValueByKey(payload, key);
            if (value != null && !value.isBlank() && !"UNKNOWN".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return fallback;
    }

    private String findValueByKey(Map<String, Object> payload, String targetKey) {
        String normalizedTarget = normalizeKey(targetKey);
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (normalizeKey(entry.getKey()).equals(normalizedTarget)) {
                return entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim();
            }
        }
        return null;
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase();
    }

    public void invalidateOwnerCache(String ownerUsername) {
        redisCacheService.evictByPrefix(DASHBOARD_CACHE_PREFIX + ownerUsername + ":");
    }

    private String cacheKey(String ownerUsername, String suffix) {
        return DASHBOARD_CACHE_PREFIX + ownerUsername + ":" + suffix;
    }

    private String fusionKeySuffix(Long fusionTaskId) {
        return fusionTaskId == null ? "latest" : String.valueOf(fusionTaskId);
    }

    private Map<String, Object> emptyTrend() {
        return Map.of(
            "dates", List.of("鍛ㄤ竴", "鍛ㄤ簩", "鍛ㄤ笁", "鍛ㄥ洓", "鍛ㄤ簲", "鍛ㄥ叚", "鍛ㄦ棩"),
            "rates", List.of(0, 0, 0, 0, 0, 0, 0),
            "predicted", List.of(0, 0, 0, 0, 0, 0, 0)
        );
    }

    private TargetTableInfo resolveTargetTable(String ownerUsername, Long fusionTaskId) {
        List<TargetTableInfo> rows;
        if (fusionTaskId == null) {
            rows = jdbcTemplate.query(
                """
                SELECT id, target_table, fusion_rows
                  FROM fusion_task_record
                 WHERE owner_username=? AND status='COMPLETED'
                 ORDER BY id DESC
                 LIMIT 1
                """,
                (rs, i) -> new TargetTableInfo(rs.getLong("id"), rs.getString("target_table"), rs.getInt("fusion_rows")),
                ownerUsername
            );
        } else {
            rows = jdbcTemplate.query(
                """
                SELECT id, target_table, fusion_rows
                  FROM fusion_task_record
                 WHERE owner_username=? AND id=? AND status='COMPLETED'
                 LIMIT 1
                """,
                (rs, i) -> new TargetTableInfo(rs.getLong("id"), rs.getString("target_table"), rs.getInt("fusion_rows")),
                ownerUsername,
                fusionTaskId
            );
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema=? AND table_name=?",
            Integer.class,
            stagingSchema,
            tableName
        );
        return count != null && count > 0;
    }

    private String stagingTableRef(String tableName) {
        return stagingSchema + "." + tableName;
    }

    private String sanitizeSchemaName(String schemaName) {
        String normalized = schemaName == null ? "" : schemaName.trim();
        if (!SAFE_SCHEMA_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("staging schema 閰嶇疆涓嶅悎娉");
        }
        return normalized;
    }

    private String sanitizeTableName(String tableName) {
        if (tableName == null || !SAFE_TABLE_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("鐩爣琛ㄥ悕涓嶅悎娉");
        }
        return tableName;
    }

    private int percent(int part, int total) {
        if (total <= 0) return 0;
        return BigDecimal.valueOf(part)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
            .intValue();
    }

    private static class GroupStats {
        int total;
        int unknown;
        Set<String> distinctPayload = new HashSet<>();
    }

    private record TargetTableInfo(Long fusionTaskId, String targetTable, Integer fusionRows) {
    }
}


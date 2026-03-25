package com.audit.data.service.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
/**
 * 清洗配置领域服务：管理规则与策略的增删改查及默认配置初始化。
 */
public class CleanConfigService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CleanConfigService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listCleanRules(String ownerUsername) {
        ensureDefaultCleanConfig(ownerUsername);
        return jdbcTemplate.query(
            "SELECT id,name,category,file_name,enabled,remark,updated_at FROM clean_rule_record WHERE owner_username=? ORDER BY updated_at DESC,id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("name", rs.getString("name"));
                row.put("category", rs.getString("category"));
                row.put("fileName", nvl(rs.getString("file_name")));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("remark", nvl(rs.getString("remark")));
                row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
                return row;
            },
            ownerUsername
        );
    }

    public Map<String, Object> uploadCleanRule(String ownerUsername, Map<String, Object> payload) {
        String name = text(payload.get("name"));
        String fileName = text(payload.get("fileName"));
        String content = text(payload.get("content"));
        String remark = text(payload.get("remark"));
        if (isBlank(name) || isBlank(fileName) || isBlank(content)) {
            throw new IllegalArgumentException("规则名称、文件和内容不能为空");
        }

        String now = now();
        Long id = insertAndGetId(
            "INSERT INTO clean_rule_record(owner_username,name,category,file_name,content,enabled,remark,created_at,updated_at) VALUES(?,?, 'USER',?,?,1,?,?,?)",
            ownerUsername, name, fileName, content, remark, now, now
        );

        Map<String, Object> row = new HashMap<>();
        row.put("id", String.valueOf(id));
        row.put("name", name);
        row.put("category", "USER");
        row.put("fileName", fileName);
        row.put("enabled", true);
        row.put("remark", remark);
        row.put("updatedAt", now);
        return row;
    }

    public Map<String, Object> toggleCleanRule(String ownerUsername, Long id, boolean enabled) {
        int updated = jdbcTemplate.update(
            "UPDATE clean_rule_record SET enabled=?, updated_at=? WHERE owner_username=? AND id=?",
            enabled ? 1 : 0, now(), ownerUsername, id
        );
        if (updated == 0) {
            throw new IllegalArgumentException("规则不存在");
        }
        return Map.of("id", String.valueOf(id), "enabled", enabled);
    }

    public Map<String, Object> getCleanRuleDetail(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,name,category,file_name,content,enabled,remark,updated_at FROM clean_rule_record WHERE owner_username=? AND id=?",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("name", rs.getString("name"));
                row.put("category", rs.getString("category"));
                row.put("fileName", nvl(rs.getString("file_name")));
                row.put("content", nvl(rs.getString("content")));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("remark", nvl(rs.getString("remark")));
                row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
                return row;
            },
            ownerUsername,
            id
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("规则不存在");
        }
        return rows.get(0);
    }

    public Map<String, Object> updateCleanRule(String ownerUsername, Long id, Map<String, Object> payload) {
        Map<String, Object> existing = getCleanRuleDetail(ownerUsername, id);
        if ("SYSTEM".equalsIgnoreCase(String.valueOf(existing.get("category")))) {
            throw new IllegalArgumentException("系统规则不允许编辑");
        }

        String name = text(payload.get("name"));
        String fileName = text(payload.get("fileName"));
        String content = text(payload.get("content"));
        String remark = text(payload.get("remark"));
        if (isBlank(name) || isBlank(fileName) || isBlank(content)) {
            throw new IllegalArgumentException("规则名称、文件和内容不能为空");
        }

        int updated = jdbcTemplate.update(
            "UPDATE clean_rule_record SET name=?, file_name=?, content=?, remark=?, updated_at=? WHERE owner_username=? AND id=?",
            name,
            fileName,
            content,
            remark,
            now(),
            ownerUsername,
            id
        );
        if (updated == 0) {
            throw new IllegalArgumentException("规则不存在");
        }
        return getCleanRuleDetail(ownerUsername, id);
    }

    public void deleteCleanRule(String ownerUsername, Long id) {
        Integer cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_rule_record WHERE owner_username=? AND id=? AND category='SYSTEM'",
            Integer.class,
            ownerUsername,
            id
        );
        if (cnt != null && cnt > 0) {
            throw new IllegalArgumentException("系统规则不允许删除");
        }

        int deleted = jdbcTemplate.update("DELETE FROM clean_rule_record WHERE owner_username=? AND id=?", ownerUsername, id);
        if (deleted == 0) {
            throw new IllegalArgumentException("规则不存在");
        }
    }

    public List<Map<String, Object>> listCleanStrategies(String ownerUsername) {
        ensureDefaultCleanConfig(ownerUsername);
        return jdbcTemplate.query(
            "SELECT id,name,code,content,remark,built_in,enabled,updated_at FROM clean_strategy_record WHERE owner_username=? ORDER BY updated_at DESC,id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("name", rs.getString("name"));
                row.put("code", rs.getString("code"));
                row.put("content", nvl(rs.getString("content")));
                row.put("remark", nvl(rs.getString("remark")));
                row.put("builtIn", rs.getBoolean("built_in"));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
                return row;
            },
            ownerUsername
        );
    }

    public Map<String, Object> createCleanStrategy(String ownerUsername, Map<String, Object> payload) {
        String name = text(payload.get("name"));
        String code = text(payload.get("code"));
        String content = text(payload.get("content"));
        String remark = text(payload.get("remark"));
        if (isBlank(name) || isBlank(code)) {
            throw new IllegalArgumentException("策略名称和编码不能为空");
        }

        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_strategy_record WHERE owner_username=? AND code=?",
            Integer.class,
            ownerUsername,
            code
        );
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("策略编码已存在");
        }

        String now = now();
        Long id = insertAndGetId(
            "INSERT INTO clean_strategy_record(owner_username,name,code,content,remark,built_in,enabled,created_at,updated_at) VALUES(?,?,?,?,?,0,1,?,?)",
            ownerUsername,
            name,
            code,
            content,
            remark,
            now,
            now
        );

        return Map.of(
            "id", String.valueOf(id),
            "name", name,
            "code", code,
            "content", content,
            "remark", remark,
            "builtIn", false,
            "enabled", true,
            "updatedAt", now
        );
    }

    public Map<String, Object> toggleCleanStrategy(String ownerUsername, Long id, boolean enabled) {
        int updated = jdbcTemplate.update(
            "UPDATE clean_strategy_record SET enabled=?, updated_at=? WHERE owner_username=? AND id=?",
            enabled ? 1 : 0, now(), ownerUsername, id
        );
        if (updated == 0) {
            throw new IllegalArgumentException("策略不存在");
        }
        return Map.of("id", String.valueOf(id), "enabled", enabled);
    }

    public Map<String, Object> getCleanStrategyDetail(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,name,code,content,remark,built_in,enabled,updated_at FROM clean_strategy_record WHERE owner_username=? AND id=?",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("name", rs.getString("name"));
                row.put("code", rs.getString("code"));
                row.put("content", nvl(rs.getString("content")));
                row.put("remark", nvl(rs.getString("remark")));
                row.put("builtIn", rs.getBoolean("built_in"));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
                return row;
            },
            ownerUsername,
            id
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("策略不存在");
        }
        return rows.get(0);
    }

    public Map<String, Object> updateCleanStrategy(String ownerUsername, Long id, Map<String, Object> payload) {
        Map<String, Object> existing = getCleanStrategyDetail(ownerUsername, id);
        if (Boolean.TRUE.equals(existing.get("builtIn"))) {
            throw new IllegalArgumentException("系统策略不允许编辑");
        }

        String name = text(payload.get("name"));
        String code = text(payload.get("code"));
        String content = text(payload.get("content"));
        String remark = text(payload.get("remark"));
        if (isBlank(name) || isBlank(code)) {
            throw new IllegalArgumentException("策略名称和编码不能为空");
        }

        Integer duplicate = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_strategy_record WHERE owner_username=? AND code=? AND id<>?",
            Integer.class,
            ownerUsername,
            code,
            id
        );
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("策略编码已存在");
        }

        int updated = jdbcTemplate.update(
            "UPDATE clean_strategy_record SET name=?, code=?, content=?, remark=?, updated_at=? WHERE owner_username=? AND id=?",
            name,
            code,
            content,
            remark,
            now(),
            ownerUsername,
            id
        );
        if (updated == 0) {
            throw new IllegalArgumentException("策略不存在");
        }
        return getCleanStrategyDetail(ownerUsername, id);
    }

    public void deleteCleanStrategy(String ownerUsername, Long id) {
        Integer builtIn;
        try {
            builtIn = jdbcTemplate.queryForObject(
                "SELECT built_in FROM clean_strategy_record WHERE owner_username=? AND id=?",
                Integer.class,
                ownerUsername,
                id
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("策略不存在");
        }
        if (builtIn == 1) {
            throw new IllegalArgumentException("系统策略不允许删除");
        }

        jdbcTemplate.update("DELETE FROM clean_strategy_record WHERE owner_username=? AND id=?", ownerUsername, id);
    }

    public void ensureDefaultCleanConfig(String ownerUsername) {
        normalizeSystemRuleName(ownerUsername, "fill_null_with_default", "空值填充规则");
        normalizeSystemRuleName(ownerUsername, "normalize_fields", "字段标准化规则");
        ensureSystemRule(ownerUsername, "空值填充规则", "fill_null_with_default");
        ensureSystemRule(ownerUsername, "字段标准化规则", "normalize_fields");

        normalizeSystemStrategyName(ownerUsername, "DEDUP_AND_FILL", "去重+空值补齐");
        normalizeSystemStrategyName(ownerUsername, "STANDARDIZE", "字段标准化");
        normalizeSystemStrategyName(ownerUsername, "OUTLIER_REMOVE", "异常值剔除");
        ensureSystemStrategy(ownerUsername, "去重+空值补齐", "DEDUP_AND_FILL");
        ensureSystemStrategy(ownerUsername, "字段标准化", "STANDARDIZE");
        ensureSystemStrategy(ownerUsername, "异常值剔除", "OUTLIER_REMOVE");
    }

    public Map<String, Object> getEnabledStrategy(String ownerUsername, String code) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,name,code FROM clean_strategy_record WHERE owner_username=? AND code=? AND enabled=1",
            (rs, i) -> Map.of("id", rs.getLong("id"), "name", rs.getString("name"), "code", rs.getString("code")),
            ownerUsername,
            code
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public List<Map<String, Object>> listFusionKeySynonyms(String ownerUsername) {
        ensureDefaultFusionKeySynonyms(ownerUsername);
        return jdbcTemplate.query(
            "SELECT id,canonical_key,aliases_json,built_in,enabled,remark,updated_at FROM fusion_key_synonym_record WHERE owner_username=? ORDER BY built_in DESC,updated_at DESC,id DESC",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("canonicalKey", rs.getString("canonical_key"));
                row.put("aliases", fromJsonToStringList(rs.getString("aliases_json")));
                row.put("builtIn", rs.getBoolean("built_in"));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("remark", nvl(rs.getString("remark")));
                row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
                return row;
            },
            ownerUsername
        );
    }

    public Map<String, Object> createFusionKeySynonym(String ownerUsername, Map<String, Object> payload) {
        String canonicalKey = text(payload.get("canonicalKey"));
        List<String> aliases = normalizeAliases(payload.get("aliases"), canonicalKey);
        String remark = text(payload.get("remark"));
        if (isBlank(canonicalKey)) {
            throw new IllegalArgumentException("标准主键不能为空");
        }

        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM fusion_key_synonym_record WHERE owner_username=? AND canonical_key=?",
            Integer.class,
            ownerUsername,
            canonicalKey
        );
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("标准主键已存在");
        }

        String now = now();
        Long id = insertAndGetId(
            "INSERT INTO fusion_key_synonym_record(owner_username,canonical_key,aliases_json,built_in,enabled,remark,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
            ownerUsername,
            canonicalKey,
            toJson(aliases),
            0,
            1,
            remark,
            now,
            now
        );

        Map<String, Object> created = getFusionKeySynonymDetail(ownerUsername, id);
        recordFusionKeySynonymHistory(ownerUsername, id, canonicalKey, "CREATE", null, created, ownerUsername);
        return created;
    }

    public Map<String, Object> updateFusionKeySynonym(String ownerUsername, Long id, Map<String, Object> payload) {
        Map<String, Object> existing = getFusionKeySynonymDetail(ownerUsername, id);
        boolean builtIn = Boolean.TRUE.equals(existing.get("builtIn"));
        String canonicalKey = text(payload.get("canonicalKey"));
        if (isBlank(canonicalKey)) {
            throw new IllegalArgumentException("标准主键不能为空");
        }
        if (builtIn && !canonicalKey.equals(String.valueOf(existing.get("canonicalKey")))) {
            throw new IllegalArgumentException("系统映射不允许修改标准主键");
        }

        Integer duplicate = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM fusion_key_synonym_record WHERE owner_username=? AND canonical_key=? AND id<>?",
            Integer.class,
            ownerUsername,
            canonicalKey,
            id
        );
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("标准主键已存在");
        }

        List<String> aliases = normalizeAliases(payload.get("aliases"), canonicalKey);
        String remark = text(payload.get("remark"));
        int updated = jdbcTemplate.update(
            "UPDATE fusion_key_synonym_record SET canonical_key=?, aliases_json=?, remark=?, updated_at=? WHERE owner_username=? AND id=?",
            canonicalKey,
            toJson(aliases),
            remark,
            now(),
            ownerUsername,
            id
        );
        if (updated == 0) {
            throw new IllegalArgumentException("主键映射不存在");
        }
        Map<String, Object> current = getFusionKeySynonymDetail(ownerUsername, id);
        recordFusionKeySynonymHistory(ownerUsername, id, canonicalKey, "UPDATE", existing, current, ownerUsername);
        return current;
    }

    public Map<String, Object> toggleFusionKeySynonym(String ownerUsername, Long id, boolean enabled) {
        Map<String, Object> existing = getFusionKeySynonymDetail(ownerUsername, id);
        int updated = jdbcTemplate.update(
            "UPDATE fusion_key_synonym_record SET enabled=?, updated_at=? WHERE owner_username=? AND id=?",
            enabled ? 1 : 0,
            now(),
            ownerUsername,
            id
        );
        if (updated == 0) {
            throw new IllegalArgumentException("主键映射不存在");
        }
        Map<String, Object> current = getFusionKeySynonymDetail(ownerUsername, id);
        recordFusionKeySynonymHistory(
            ownerUsername,
            id,
            String.valueOf(current.get("canonicalKey")),
            enabled ? "ENABLE" : "DISABLE",
            existing,
            current,
            ownerUsername
        );
        return Map.of("id", String.valueOf(id), "enabled", enabled);
    }

    public Map<String, Object> getFusionKeySynonymDetail(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,canonical_key,aliases_json,built_in,enabled,remark,updated_at FROM fusion_key_synonym_record WHERE owner_username=? AND id=?",
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("canonicalKey", rs.getString("canonical_key"));
                row.put("aliases", fromJsonToStringList(rs.getString("aliases_json")));
                row.put("builtIn", rs.getBoolean("built_in"));
                row.put("enabled", rs.getBoolean("enabled"));
                row.put("remark", nvl(rs.getString("remark")));
                row.put("updatedAt", formatDateTime(rs.getTimestamp("updated_at")));
                return row;
            },
            ownerUsername,
            id
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("主键映射不存在");
        }
        return rows.get(0);
    }

    public void deleteFusionKeySynonym(String ownerUsername, Long id) {
        Map<String, Object> existing = getFusionKeySynonymDetail(ownerUsername, id);
        Integer builtIn;
        try {
            builtIn = jdbcTemplate.queryForObject(
                "SELECT built_in FROM fusion_key_synonym_record WHERE owner_username=? AND id=?",
                Integer.class,
                ownerUsername,
                id
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("主键映射不存在");
        }
        if (builtIn != null && builtIn == 1) {
            throw new IllegalArgumentException("系统映射不允许删除");
        }
        recordFusionKeySynonymHistory(
            ownerUsername,
            id,
            String.valueOf(existing.get("canonicalKey")),
            "DELETE",
            existing,
            null,
            ownerUsername
        );
        jdbcTemplate.update("DELETE FROM fusion_key_synonym_record WHERE owner_username=? AND id=?", ownerUsername, id);
    }

    public List<Map<String, Object>> listFusionKeySynonymHistory(String ownerUsername, Long id, Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 50 : Math.min(limit, 500);
        return jdbcTemplate.query(
            "SELECT id,synonym_id,canonical_key,version_no,action_type,before_json,after_json,actor_username,created_at FROM fusion_key_synonym_history_record WHERE owner_username=? AND synonym_id=? ORDER BY version_no DESC,id DESC LIMIT " + safeLimit,
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("synonymId", String.valueOf(rs.getLong("synonym_id")));
                row.put("canonicalKey", rs.getString("canonical_key"));
                row.put("versionNo", rs.getInt("version_no"));
                row.put("actionType", rs.getString("action_type"));
                row.put("beforeData", fromJsonToMap(rs.getString("before_json")));
                row.put("afterData", fromJsonToMap(rs.getString("after_json")));
                row.put("actorUsername", rs.getString("actor_username"));
                row.put("createdAt", formatDateTime(rs.getTimestamp("created_at")));
                return row;
            },
            ownerUsername,
            id
        );
    }

    public List<Map<String, Object>> listFusionKeySynonymHistoryByCanonicalKey(String ownerUsername, String canonicalKey, Integer limit) {
        String key = text(canonicalKey);
        if (isBlank(key)) {
            throw new IllegalArgumentException("标准主键不能为空");
        }
        int safeLimit = (limit == null || limit <= 0) ? 100 : Math.min(limit, 1000);
        return jdbcTemplate.query(
            "SELECT id,synonym_id,canonical_key,version_no,action_type,before_json,after_json,actor_username,created_at FROM fusion_key_synonym_history_record WHERE owner_username=? AND canonical_key=? ORDER BY created_at DESC,id DESC LIMIT " + safeLimit,
            (rs, i) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", String.valueOf(rs.getLong("id")));
                row.put("synonymId", String.valueOf(rs.getLong("synonym_id")));
                row.put("canonicalKey", rs.getString("canonical_key"));
                row.put("versionNo", rs.getInt("version_no"));
                row.put("actionType", rs.getString("action_type"));
                row.put("beforeData", fromJsonToMap(rs.getString("before_json")));
                row.put("afterData", fromJsonToMap(rs.getString("after_json")));
                row.put("actorUsername", rs.getString("actor_username"));
                row.put("createdAt", formatDateTime(rs.getTimestamp("created_at")));
                return row;
            },
            ownerUsername,
            key
        );
    }

    private void ensureSystemRule(String ownerUsername, String name, String content) {
        String now = now();
        jdbcTemplate.update(
            """
            INSERT INTO clean_rule_record(owner_username,name,category,file_name,content,enabled,remark,created_at,updated_at)
            SELECT ?, ?, 'SYSTEM', '-', ?, 1, '系统默认规则', ?, ?
              FROM dual
             WHERE NOT EXISTS (
                   SELECT 1
                     FROM clean_rule_record
                    WHERE owner_username=? AND category='SYSTEM' AND content=?
             )
            """,
            ownerUsername,
            name,
            content,
            now,
            now,
            ownerUsername,
            content
        );
    }

    private void normalizeSystemRuleName(String ownerUsername, String content, String canonicalName) {
        jdbcTemplate.update(
            "UPDATE clean_rule_record SET name=?, updated_at=? WHERE owner_username=? AND category='SYSTEM' AND content=? AND name<>?",
            canonicalName,
            now(),
            ownerUsername,
            content,
            canonicalName
        );
    }

    private void ensureSystemStrategy(String ownerUsername, String name, String code) {
        String now = now();
        jdbcTemplate.update(
            """
            INSERT INTO clean_strategy_record(owner_username,name,code,content,remark,built_in,enabled,created_at,updated_at)
            SELECT ?, ?, ?, '', '系统默认策略', 1, 1, ?, ?
              FROM dual
             WHERE NOT EXISTS (
                   SELECT 1
                     FROM clean_strategy_record
                    WHERE owner_username=? AND code=?
             )
            """,
            ownerUsername,
            name,
            code,
            now,
            now,
            ownerUsername,
            code
        );

        jdbcTemplate.update(
            "UPDATE clean_strategy_record SET built_in=1, enabled=1, name=?, remark=?, updated_at=? WHERE owner_username=? AND code=? AND (built_in<>1 OR enabled<>1 OR name<>? OR COALESCE(remark,'')<>?)",
            name,
            "系统默认策略",
            now(),
            ownerUsername,
            code,
            name,
            "系统默认策略"
        );
    }

    private void normalizeSystemStrategyName(String ownerUsername, String code, String canonicalName) {
        jdbcTemplate.update(
            "UPDATE clean_strategy_record SET name=?, updated_at=? WHERE owner_username=? AND built_in=1 AND code=? AND name<>?",
            canonicalName,
            now(),
            ownerUsername,
            code,
            canonicalName
        );
    }

    private void ensureDefaultFusionKeySynonyms(String ownerUsername) {
        ensureSystemFusionKeySynonym(ownerUsername, "整改单位ID", List.of("单位ID", "dept_id", "organization_id"));
        ensureSystemFusionKeySynonym(ownerUsername, "整改事项ID", List.of("事项ID", "issue_id", "rect_id"));
        ensureSystemFusionKeySynonym(ownerUsername, "问题类别", List.of("类别", "category", "problem_category"));
        ensureSystemFusionKeySynonym(ownerUsername, "统计日期", List.of("日期", "date", "stat_date"));
    }

    private void ensureSystemFusionKeySynonym(String ownerUsername, String canonicalKey, List<String> aliases) {
        String now = now();
        String aliasesJson = toJson(normalizeAliases(aliases, canonicalKey));
        jdbcTemplate.update(
            """
            INSERT INTO fusion_key_synonym_record(owner_username,canonical_key,aliases_json,built_in,enabled,remark,created_at,updated_at)
            SELECT ?, ?, ?, 1, 1, '系统默认映射', ?, ?
              FROM dual
             WHERE NOT EXISTS (
                   SELECT 1
                     FROM fusion_key_synonym_record
                    WHERE owner_username=? AND canonical_key=?
             )
            """,
            ownerUsername,
            canonicalKey,
            aliasesJson,
            now,
            now,
            ownerUsername,
            canonicalKey
        );
        jdbcTemplate.update(
            "UPDATE fusion_key_synonym_record SET aliases_json=?, built_in=1, enabled=1, remark='系统默认映射', updated_at=? WHERE owner_username=? AND canonical_key=? AND (COALESCE(aliases_json,'')<>? OR built_in<>1 OR enabled<>1 OR COALESCE(remark,'')<>'系统默认映射')",
            aliasesJson,
            now(),
            ownerUsername,
            canonicalKey,
            aliasesJson
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化失败", ex);
        }
    }

    private String toJsonNullable(Object value) {
        if (value == null) {
            return null;
        }
        return toJson(value);
    }

    private List<String> fromJsonToStringList(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> fromJsonToMap(String json) {
        if (isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void recordFusionKeySynonymHistory(
        String ownerUsername,
        Long synonymId,
        String canonicalKey,
        String actionType,
        Map<String, Object> beforeData,
        Map<String, Object> afterData,
        String actorUsername
    ) {
        Integer nextVersion = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version_no),0) + 1 FROM fusion_key_synonym_history_record WHERE owner_username=? AND synonym_id=?",
            Integer.class,
            ownerUsername,
            synonymId
        );
        int versionNo = nextVersion == null ? 1 : nextVersion;
        jdbcTemplate.update(
            "INSERT INTO fusion_key_synonym_history_record(synonym_id,owner_username,canonical_key,version_no,action_type,before_json,after_json,actor_username,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
            synonymId,
            ownerUsername,
            text(canonicalKey),
            versionNo,
            actionType,
            toJsonNullable(beforeData),
            toJsonNullable(afterData),
            text(actorUsername),
            now()
        );
    }

    private List<String> normalizeAliases(Object value, String canonicalKey) {
        List<String> source;
        if (value instanceof List<?> list) {
            source = list.stream().map(String::valueOf).toList();
        } else if (value instanceof String textValue) {
            if (isBlank(textValue)) {
                source = List.of();
            } else {
                source = List.of(textValue.split("[,，|+]"));
            }
        } else {
            source = List.of();
        }
        return normalizeAliases(source, canonicalKey);
    }

    private List<String> normalizeAliases(List<String> aliases, String canonicalKey) {
        Set<String> set = new LinkedHashSet<>();
        String canonicalLower = text(canonicalKey).toLowerCase();
        for (String alias : aliases) {
            String item = text(alias);
            if (item.isEmpty()) {
                continue;
            }
            if (item.toLowerCase().equals(canonicalLower)) {
                continue;
            }
            set.add(item);
        }
        return new ArrayList<>(set);
    }

    @SuppressWarnings("null")
    private Long insertAndGetId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("新增失败");
        }
        return key.longValue();
    }

    private static String now() {
        return DATE_TIME_FORMATTER.format(Instant.now());
    }

    private static String formatDateTime(Timestamp ts) {
        if (ts == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(ts.toInstant());
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
}




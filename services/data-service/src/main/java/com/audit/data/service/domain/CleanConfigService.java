package com.audit.data.service.domain;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public CleanConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
            throw new IllegalArgumentException("瑙勫垯鍚嶇О銆佹枃浠跺拰鍐呭涓嶈兘涓虹┖");
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
            throw new IllegalArgumentException("瑙勫垯涓嶅瓨鍦");
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
            throw new IllegalArgumentException("瑙勫垯涓嶅瓨鍦");
        }
        return rows.get(0);
    }

    public Map<String, Object> updateCleanRule(String ownerUsername, Long id, Map<String, Object> payload) {
        Map<String, Object> existing = getCleanRuleDetail(ownerUsername, id);
        if ("SYSTEM".equalsIgnoreCase(String.valueOf(existing.get("category")))) {
            throw new IllegalArgumentException("绯荤粺瑙勫垯涓嶅厑璁哥紪杈");
        }

        String name = text(payload.get("name"));
        String fileName = text(payload.get("fileName"));
        String content = text(payload.get("content"));
        String remark = text(payload.get("remark"));
        if (isBlank(name) || isBlank(fileName) || isBlank(content)) {
            throw new IllegalArgumentException("瑙勫垯鍚嶇О銆佹枃浠跺拰鍐呭涓嶈兘涓虹┖");
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
            throw new IllegalArgumentException("瑙勫垯涓嶅瓨鍦");
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
            throw new IllegalArgumentException("绯荤粺瑙勫垯涓嶅厑璁稿垹闄");
        }

        int deleted = jdbcTemplate.update("DELETE FROM clean_rule_record WHERE owner_username=? AND id=?", ownerUsername, id);
        if (deleted == 0) {
            throw new IllegalArgumentException("瑙勫垯涓嶅瓨鍦");
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
            throw new IllegalArgumentException("绛栫暐鍚嶇О鍜岀紪鐮佷笉鑳戒负绌");
        }

        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_strategy_record WHERE owner_username=? AND code=?",
            Integer.class,
            ownerUsername,
            code
        );
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("绛栫暐缂栫爜宸插瓨鍦");
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
            throw new IllegalArgumentException("绛栫暐涓嶅瓨鍦");
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
            throw new IllegalArgumentException("绛栫暐涓嶅瓨鍦");
        }
        return rows.get(0);
    }

    public Map<String, Object> updateCleanStrategy(String ownerUsername, Long id, Map<String, Object> payload) {
        Map<String, Object> existing = getCleanStrategyDetail(ownerUsername, id);
        if (Boolean.TRUE.equals(existing.get("builtIn"))) {
            throw new IllegalArgumentException("绯荤粺绛栫暐涓嶅厑璁哥紪杈");
        }

        String name = text(payload.get("name"));
        String code = text(payload.get("code"));
        String content = text(payload.get("content"));
        String remark = text(payload.get("remark"));
        if (isBlank(name) || isBlank(code)) {
            throw new IllegalArgumentException("绛栫暐鍚嶇О鍜岀紪鐮佷笉鑳戒负绌");
        }

        Integer duplicate = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_strategy_record WHERE owner_username=? AND code=? AND id<>?",
            Integer.class,
            ownerUsername,
            code,
            id
        );
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("绛栫暐缂栫爜宸插瓨鍦");
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
            throw new IllegalArgumentException("绛栫暐涓嶅瓨鍦");
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
            throw new IllegalArgumentException("绛栫暐涓嶅瓨鍦");
        }
        if (builtIn == 1) {
            throw new IllegalArgumentException("绯荤粺绛栫暐涓嶅厑璁稿垹闄");
        }

        jdbcTemplate.update("DELETE FROM clean_strategy_record WHERE owner_username=? AND id=?", ownerUsername, id);
    }

    public void ensureDefaultCleanConfig(String ownerUsername) {
        ensureSystemRule(ownerUsername, "绌哄€煎～鍏呰鍒", "fill_null_with_default");
        ensureSystemRule(ownerUsername, "瀛楁鏍囧噯鍖栬鍒", "normalize_fields");
        cleanupDuplicateSystemRules(ownerUsername);

        ensureSystemStrategy(ownerUsername, "鍘婚噸+绌哄€艰ˉ榻", "DEDUP_AND_FILL");
        ensureSystemStrategy(ownerUsername, "瀛楁鏍囧噯鍖", "STANDARDIZE");
        ensureSystemStrategy(ownerUsername, "寮傚父鍊煎墧闄", "OUTLIER_REMOVE");
        cleanupDuplicateSystemStrategies(ownerUsername);
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

    private void ensureSystemRule(String ownerUsername, String name, String content) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_rule_record WHERE owner_username=? AND category='SYSTEM' AND name=? AND content=?",
            Integer.class,
            ownerUsername,
            name,
            content
        );
        if (count != null && count > 0) {
            return;
        }

        String now = now();
        jdbcTemplate.update(
            "INSERT INTO clean_rule_record(owner_username,name,category,file_name,content,enabled,remark,created_at,updated_at) VALUES(?, ?, 'SYSTEM', '-', ?, 1, '系统默认规则', ?, ?)",
            ownerUsername,
            name,
            content,
            now,
            now
        );
    }

    private void cleanupDuplicateSystemRules(String ownerUsername) {
        List<Long> duplicateIds = jdbcTemplate.query(
            """
            SELECT r.id
              FROM clean_rule_record r
              JOIN (
                    SELECT owner_username, name, content, MIN(id) AS keep_id, COUNT(1) AS cnt
                      FROM clean_rule_record
                     WHERE owner_username=? AND category='SYSTEM'
                     GROUP BY owner_username, name, content
                    HAVING COUNT(1) > 1
                   ) dup
                ON dup.owner_username = r.owner_username
               AND dup.name = r.name
               AND dup.content = r.content
             WHERE r.id <> dup.keep_id
            """,
            (rs, i) -> rs.getLong("id"),
            ownerUsername
        );
        if (!duplicateIds.isEmpty()) {
            jdbcTemplate.batchUpdate(
                "DELETE FROM clean_rule_record WHERE id=? AND owner_username=?",
                duplicateIds,
                duplicateIds.size(),
                (ps, id) -> {
                    ps.setLong(1, id);
                    ps.setString(2, ownerUsername);
                }
            );
        }
    }

    private void ensureSystemStrategy(String ownerUsername, String name, String code) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM clean_strategy_record WHERE owner_username=? AND built_in=1 AND code=?",
            Integer.class,
            ownerUsername,
            code
        );
        if (count != null && count > 0) {
            return;
        }

        String now = now();
        jdbcTemplate.update(
            "INSERT INTO clean_strategy_record(owner_username,name,code,content,remark,built_in,enabled,created_at,updated_at) VALUES(?,?,?,?,?,1,1,?,?)",
            ownerUsername,
            name,
            code,
            "",
            "绯荤粺榛樿绛栫暐",
            now,
            now
        );
    }

    private void cleanupDuplicateSystemStrategies(String ownerUsername) {
        List<Long> duplicateIds = jdbcTemplate.query(
            """
            SELECT s.id
              FROM clean_strategy_record s
              JOIN (
                    SELECT owner_username, code, MIN(id) AS keep_id, COUNT(1) AS cnt
                      FROM clean_strategy_record
                     WHERE owner_username=? AND built_in=1
                     GROUP BY owner_username, code
                    HAVING COUNT(1) > 1
                   ) dup
                ON dup.owner_username = s.owner_username
               AND dup.code = s.code
             WHERE s.id <> dup.keep_id
            """,
            (rs, i) -> rs.getLong("id"),
            ownerUsername
        );
        if (!duplicateIds.isEmpty()) {
            jdbcTemplate.batchUpdate(
                "DELETE FROM clean_strategy_record WHERE id=? AND owner_username=?",
                duplicateIds,
                duplicateIds.size(),
                (ps, id) -> {
                    ps.setLong(1, id);
                    ps.setString(2, ownerUsername);
                }
            );
        }
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
            throw new IllegalStateException("鏂板澶辫触");
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


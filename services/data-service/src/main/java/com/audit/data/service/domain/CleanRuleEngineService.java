package com.audit.data.service.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
/**
 * 清洗规则引擎：解析规则定义并对标准化数据执行字段级清洗动作。
 */
public class CleanRuleEngineService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CleanRuleEngineService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void applyCleanStrategy(String ownerUsername, String outputTable, String strategyCode, List<String> ruleNames) {
        String normalized = text(strategyCode).toUpperCase();

        if (normalized.contains("DEDUP")) {
            jdbcTemplate.update(
                "DELETE t1 FROM " + outputTable + " t1 JOIN " + outputTable + " t2 ON t1.id > t2.id AND t1.normalized_json = t2.normalized_json"
            );
        }

        if (normalized.contains("STANDARD")) {
            jdbcTemplate.update("UPDATE " + outputTable + " SET normalized_json=LOWER(normalized_json)");
        }

        if (normalized.contains("OUTLIER")) {
            jdbcTemplate.update("DELETE FROM " + outputTable + " WHERE CHAR_LENGTH(normalized_json) > 8000");
        }

        List<RuleAction> actions = loadRuleActions(ownerUsername, ruleNames);
        if (!actions.isEmpty()) {
            applyRuleActionsToRows(outputTable, actions);
        } else if (ruleNames != null && ruleNames.stream().anyMatch(name -> String.valueOf(name).contains("绌哄€"))) {
            jdbcTemplate.update("UPDATE " + outputTable + " SET normalized_json=REPLACE(normalized_json, ':\"\"', ':\"UNKNOWN\"')");
        }
    }

    private List<RuleAction> loadRuleActions(String ownerUsername, List<String> ruleNames) {
        if (ruleNames == null || ruleNames.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueNames = new LinkedHashSet<>();
        for (String name : ruleNames) {
            if (!isBlank(name)) {
                uniqueNames.add(name);
            }
        }
        if (uniqueNames.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", uniqueNames.stream().map(it -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(ownerUsername);
        args.addAll(uniqueNames);

        String sql = "SELECT name,content FROM clean_rule_record WHERE owner_username=? AND enabled=1 AND name IN (" + placeholders + ") ORDER BY id ASC";
        List<Map<String, Object>> rows = jdbcTemplate.query(
            sql,
            (rs, i) -> Map.of("name", nvl(rs.getString("name")), "content", nvl(rs.getString("content"))),
            args.toArray()
        );

        List<RuleAction> actions = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String ruleName = text(row.get("name"));
            String content = text(row.get("content"));
            actions.addAll(parseRuleActions(content));

            if (ruleName.contains("绌哄€") && actions.stream().noneMatch(action -> "fill_null".equals(action.type))) {
                actions.add(new RuleAction("fill_null", "*", "UNKNOWN", "", ""));
            }
        }
        return actions;
    }

    private List<RuleAction> parseRuleActions(String content) {
        if (isBlank(content)) {
            return List.of();
        }

        String trimmed = content.trim();
        List<RuleAction> actions = new ArrayList<>();

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                if (trimmed.startsWith("{")) {
                    Map<String, Object> obj = objectMapper.readValue(trimmed, new TypeReference<>() {});
                    Object rootActions = obj.get("actions");
                    if (rootActions instanceof List<?> list) {
                        actions.addAll(parseActionList(list));
                    } else {
                        RuleAction single = parseActionMap(obj);
                        if (single != null) actions.add(single);
                    }
                } else {
                    List<Map<String, Object>> list = objectMapper.readValue(trimmed, new TypeReference<>() {});
                    actions.addAll(parseActionList(new ArrayList<>(list)));
                }
            } catch (Exception ignore) {
                // Fall back to line-based parsing below.
            }
        }

        if (!actions.isEmpty()) {
            return actions;
        }

        String[] lines = trimmed.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = line.split("\\|", -1);
            String type = parts[0].trim().toLowerCase();
            if (type.isEmpty()) {
                continue;
            }

            String field = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : "*";
            String value = parts.length > 2 ? parts[2].trim() : "";
            String from = parts.length > 2 ? parts[2].trim() : "";
            String to = parts.length > 3 ? parts[3].trim() : "";
            actions.add(new RuleAction(type, field, value, from, to));
        }

        if (actions.isEmpty() && (trimmed.contains("绌哄€") || trimmed.contains("fill_null"))) {
            actions.add(new RuleAction("fill_null", "*", "UNKNOWN", "", ""));
        }
        return actions;
    }

    @SuppressWarnings("unchecked")
    private List<RuleAction> parseActionList(List<?> list) {
        List<RuleAction> actions = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                RuleAction action = parseActionMap((Map<String, Object>) map);
                if (action != null) actions.add(action);
            }
        }
        return actions;
    }

    private RuleAction parseActionMap(Map<String, Object> map) {
        String type = text(map.get("type")).toLowerCase();
        if (isBlank(type)) {
            return null;
        }
        String field = text(map.get("field"));
        if (isBlank(field)) {
            field = "*";
        }
        String value = text(map.get("value"));
        String from = text(map.get("from"));
        String to = text(map.get("to"));
        return new RuleAction(type, field, value, from, to);
    }

    private void applyRuleActionsToRows(String outputTable, List<RuleAction> actions) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id, normalized_json FROM " + outputTable + " ORDER BY id ASC",
            (rs, i) -> Map.of("id", rs.getLong("id"), "json", nvl(rs.getString("normalized_json")))
        );

        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("id"));
            String json = text(row.get("json"));
            if (id == null || isBlank(json)) {
                continue;
            }

            Map<String, Object> obj;
            try {
                obj = objectMapper.readValue(json, new TypeReference<>() {});
            } catch (Exception ex) {
                continue;
            }

            boolean changed = false;
            for (RuleAction action : actions) {
                changed = applyRuleAction(obj, action) || changed;
            }

            if (changed) {
                jdbcTemplate.update(
                    "UPDATE " + outputTable + " SET normalized_json=? WHERE id=?",
                    toJson(obj),
                    id
                );
            }
        }
    }

    private boolean applyRuleAction(Map<String, Object> obj, RuleAction action) {
        String field = isBlank(action.field) ? "*" : action.field;
        String type = action.type;

        if ("remove_field".equals(type)) {
            if ("*".equals(field)) {
                return false;
            }
            return obj.remove(field) != null;
        }

        if ("*".equals(field)) {
            boolean changed = false;
            for (String key : new ArrayList<>(obj.keySet())) {
                changed = applyRuleToField(obj, key, action) || changed;
            }
            return changed;
        }

        return applyRuleToField(obj, field, action);
    }

    private boolean applyRuleToField(Map<String, Object> obj, String field, RuleAction action) {
        Object current = obj.get(field);
        String currentText = current == null ? "" : String.valueOf(current);

        return switch (action.type) {
            case "fill_null" -> {
                if (current == null || currentText.isBlank()) {
                    obj.put(field, isBlank(action.value) ? "UNKNOWN" : action.value);
                    yield true;
                }
                yield false;
            }
            case "trim" -> {
                if (current == null) yield false;
                String trimmed = currentText.trim();
                if (!trimmed.equals(currentText)) {
                    obj.put(field, trimmed);
                    yield true;
                }
                yield false;
            }
            case "lowercase" -> {
                if (current == null) yield false;
                String lowered = currentText.toLowerCase();
                if (!lowered.equals(currentText)) {
                    obj.put(field, lowered);
                    yield true;
                }
                yield false;
            }
            case "uppercase" -> {
                if (current == null) yield false;
                String uppered = currentText.toUpperCase();
                if (!uppered.equals(currentText)) {
                    obj.put(field, uppered);
                    yield true;
                }
                yield false;
            }
            case "replace" -> {
                if (current == null || isBlank(action.from)) yield false;
                String replaced = currentText.replace(action.from, nvl(action.to));
                if (!replaced.equals(currentText)) {
                    obj.put(field, replaced);
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON搴忓垪鍖栧け璐");
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

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record RuleAction(String type, String field, String value, String from, String to) {
    }
}


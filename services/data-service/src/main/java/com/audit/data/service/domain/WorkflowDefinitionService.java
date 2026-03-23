package com.audit.data.service.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
/**
 * 工作流定义领域服务：负责节点解析、依赖校验和环路检测。
 */
public class WorkflowDefinitionService {

    public List<WorkflowNode> parseWorkflowNodes(Map<String, Object> payload) {
        List<Map<String, Object>> nodeMaps = castMapList(payload.get("nodes"));
        List<WorkflowNode> nodes = new ArrayList<>();
        if (!nodeMaps.isEmpty()) {
            for (Map<String, Object> node : nodeMaps) {
                String nodeId = text(node.get("nodeId"));
                if (isBlank(nodeId)) {
                    nodeId = "node-" + (nodes.size() + 1);
                }
                String taskType = text(node.get("taskType")).toUpperCase();
                Long taskId = toLong(node.get("taskId"));
                List<String> dependsOn = castStringList(node.get("dependsOn"));
                nodes.add(new WorkflowNode(nodeId, taskType, taskId, dependsOn));
            }
            return nodes;
        }

        List<Long> cleanTaskIds = castLongList(payload.get("cleanTaskIds"));
        List<Long> fusionTaskIds = castLongList(payload.get("fusionTaskIds"));
        for (Long id : cleanTaskIds) {
            nodes.add(new WorkflowNode("clean-" + id, "CLEAN", id, List.of()));
        }
        List<String> cleanNodeIds = nodes.stream().map(WorkflowNode::nodeId).toList();
        for (Long id : fusionTaskIds) {
            nodes.add(new WorkflowNode("fusion-" + id, "FUSION", id, cleanNodeIds));
        }
        return nodes;
    }

    public void validateWorkflowNodes(List<WorkflowNode> nodes) {
        Set<String> ids = new HashSet<>();
        for (WorkflowNode node : nodes) {
            if (!ids.add(node.nodeId)) {
                throw new IllegalArgumentException("宸ヤ綔娴佽妭鐐笽D閲嶅: " + node.nodeId);
            }
            if (!Set.of("CLEAN", "FUSION").contains(node.taskType)) {
                throw new IllegalArgumentException("宸ヤ綔娴佷换鍔＄被鍨嬩粎鏀寔 CLEAN/FUSION");
            }
            if (node.taskId == null || node.taskId <= 0) {
                throw new IllegalArgumentException("宸ヤ綔娴佽妭鐐逛换鍔D涓嶅悎娉");
            }
        }
        for (WorkflowNode node : nodes) {
            for (String dep : node.dependsOn) {
                if (!ids.contains(dep)) {
                    throw new IllegalArgumentException("宸ヤ綔娴佷緷璧栬妭鐐逛笉瀛樺湪: " + dep);
                }
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> edges = new HashMap<>();
        for (WorkflowNode node : nodes) {
            indegree.put(node.nodeId, node.dependsOn.size());
            edges.putIfAbsent(node.nodeId, new ArrayList<>());
        }
        for (WorkflowNode node : nodes) {
            for (String dep : node.dependsOn) {
                edges.computeIfAbsent(dep, it -> new ArrayList<>()).add(node.nodeId);
            }
        }
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (String next : edges.getOrDefault(current, List.of())) {
                int v = indegree.get(next) - 1;
                indegree.put(next, v);
                if (v == 0) {
                    queue.offer(next);
                }
            }
        }
        if (visited != nodes.size()) {
            throw new IllegalArgumentException("宸ヤ綔娴佸瓨鍦ㄥ惊鐜緷璧");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castMapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) out.add((Map<String, Object>) map);
        }
        return out;
    }

    private List<String> castStringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private List<Long> castLongList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(WorkflowDefinitionService::toLong).filter(java.util.Objects::nonNull).toList();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    public record WorkflowNode(String nodeId, String taskType, Long taskId, List<String> dependsOn) {
    }
}


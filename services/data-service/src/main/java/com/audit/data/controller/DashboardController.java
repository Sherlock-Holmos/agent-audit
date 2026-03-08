package com.audit.data.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
public class DashboardController {

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestHeader(value = "X-User-Dept", required = false) String deptId) {
        return Map.of(
            "department", deptId == null ? "default-dept" : deptId,
            "completedRate", 86,
            "overdueCount", 12,
            "departmentRank", 3
        );
    }

    @GetMapping("/trend")
    public Map<String, Object> trend() {
        return Map.of(
            "dates", List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
            "rates", List.of(78, 80, 82, 83, 85, 86, 87),
            "predicted", List.of(79, 81, 83, 84, 86, 88, 89)
        );
    }

    @GetMapping("/heatmap")
    public Map<String, Object> heatmap() {
        return Map.of(
            "departments", List.of("财务部", "采购部", "生产部", "研发部"),
            "metrics", List.of("完成率", "超期率", "复发率", "闭环率"),
            "values", List.of(
                List.of(0, 0, 88), List.of(0, 1, 20), List.of(0, 2, 12), List.of(0, 3, 90),
                List.of(1, 0, 75), List.of(1, 1, 35), List.of(1, 2, 21), List.of(1, 3, 80),
                List.of(2, 0, 82), List.of(2, 1, 28), List.of(2, 2, 16), List.of(2, 3, 85),
                List.of(3, 0, 90), List.of(3, 1, 15), List.of(3, 2, 8),  List.of(3, 3, 93)
            )
        );
    }
}

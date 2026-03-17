package com.audit.data.controller;

import com.audit.data.service.IDataSourceService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/data/sources")
public class DataSourceController {

    private final IDataSourceService dataSourceService;

    public DataSourceController(IDataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "ok",
                "data", dataSourceService.list(user(username), keyword, type, status)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                "code", 500,
                "message", "数据源列表加载失败"
            ));
        }
    }

    @PostMapping("/database")
    public ResponseEntity<Map<String, Object>> createDatabase(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestBody Map<String, Object> payload
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "创建成功",
                "data", dataSourceService.createDatabase(user(username), payload)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/file")
    public ResponseEntity<Map<String, Object>> createFile(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam String name,
        @RequestParam(required = false) String remark,
        @RequestParam("file") MultipartFile file
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "导入成功",
                "data", dataSourceService.createFile(user(username), name, remark, file)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @GetMapping("/{id}/objects")
    public ResponseEntity<Map<String, Object>> listSourceObjects(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "ok",
                "data", dataSourceService.listSourceObjects(user(username), id)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id,
        @RequestBody Map<String, String> payload
    ) {
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "更新成功",
                "data", dataSourceService.updateStatus(user(username), id, payload.getOrDefault("status", ""))
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @PathVariable Long id
    ) {
        try {
            dataSourceService.delete(user(username), id);
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "删除成功"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", ex.getMessage()
            ));
        }
    }

    private String user(String username) {
        return (username == null || username.isBlank()) ? "anonymous" : username;
    }
}

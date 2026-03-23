package com.audit.data.controller;

import com.audit.data.application.IDataSourceApplicationService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
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

@Validated
@RestController
@RequestMapping("/api/data/sources")
/**
 * 数据源管理接口。
 */
public class DataSourceController {

    private final IDataSourceApplicationService dataSourceApplicationService;

    public DataSourceController(IDataSourceApplicationService dataSourceApplicationService) {
        this.dataSourceApplicationService = dataSourceApplicationService;
    }

    @GetMapping
    public ApiResponse<Object> list(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status
    ) {
        return ApiResponse.success("ok", dataSourceApplicationService.list(username, keyword, type, status));
    }

    @PostMapping("/database")
    public ApiResponse<Object> createDatabase(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, Object> payload
    ) {
        return ApiResponse.success("创建成功", dataSourceApplicationService.createDatabase(username, payload));
    }

    @PostMapping("/file")
    public ApiResponse<Object> createFile(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotBlank(message = "数据源名称不能为空") @RequestParam String name,
        @RequestParam(required = false) String remark,
        @NotNull(message = "上传文件不能为空") @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success("导入成功", dataSourceApplicationService.createFile(username, name, remark, file));
    }

    @GetMapping("/{id}/objects")
    public ApiResponse<Object> listSourceObjects(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "数据源ID不能为空") @Positive(message = "数据源ID必须大于0") @PathVariable Long id
    ) {
        return ApiResponse.success("ok", dataSourceApplicationService.listSourceObjects(username, id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Object> updateStatus(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "数据源ID不能为空") @Positive(message = "数据源ID必须大于0") @PathVariable Long id,
        @NotNull(message = "请求体不能为空") @RequestBody Map<String, String> payload
    ) {
        return ApiResponse.success("更新成功", dataSourceApplicationService.updateStatus(username, id, payload.getOrDefault("status", "")));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @NotNull(message = "数据源ID不能为空") @Positive(message = "数据源ID必须大于0") @PathVariable Long id
    ) {
        dataSourceApplicationService.delete(username, id);
        return ApiResponse.success("删除成功");
    }

}


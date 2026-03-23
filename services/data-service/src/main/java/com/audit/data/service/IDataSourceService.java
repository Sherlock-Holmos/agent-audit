package com.audit.data.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据源管理业务接口
 */
public interface IDataSourceService {

    // 数据源基础操作
    List<Map<String, Object>> list(String ownerUsername, String keyword, String type, String status);
    void delete(String ownerUsername, Long id);
    Map<String, Object> updateStatus(String ownerUsername, Long id, String status);

    // 数据库数据源
    Map<String, Object> createDatabase(String ownerUsername, Map<String, Object> payload);

    // 文件数据源
    Map<String, Object> createFile(String ownerUsername, String name, String remark, MultipartFile file);

    // 数据源对象查询
    List<Map<String, Object>> listSourceObjects(String ownerUsername, Long id);
}


package com.audit.data.application;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据源应用服务接口。
 */
public interface IDataSourceApplicationService {

    List<Map<String, Object>> list(String username, String keyword, String type, String status);

    Map<String, Object> createDatabase(String username, Map<String, Object> payload);

    Map<String, Object> createFile(String username, String name, String remark, MultipartFile file);

    List<Map<String, Object>> listSourceObjects(String username, Long id);

    Map<String, Object> updateStatus(String username, Long id, String status);

    void delete(String username, Long id);
}


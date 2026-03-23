package com.audit.data.application;

import com.audit.data.service.IDataSourceService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
/**
 * 数据源应用服务：统一封装数据源相关用例并处理用户上下文。
 */
public class DataSourceApplicationService implements IDataSourceApplicationService {

    private final IDataSourceService dataSourceService;

    public DataSourceApplicationService(IDataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String username, String keyword, String type, String status) {
        return dataSourceService.list(normalizeUser(username), keyword, type, status);
    }

    @Override
    @Transactional
    public Map<String, Object> createDatabase(String username, Map<String, Object> payload) {
        return dataSourceService.createDatabase(normalizeUser(username), payload);
    }

    @Override
    @Transactional
    public Map<String, Object> createFile(String username, String name, String remark, MultipartFile file) {
        return dataSourceService.createFile(normalizeUser(username), name, remark, file);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSourceObjects(String username, Long id) {
        return dataSourceService.listSourceObjects(normalizeUser(username), id);
    }

    @Override
    @Transactional
    public Map<String, Object> updateStatus(String username, Long id, String status) {
        return dataSourceService.updateStatus(normalizeUser(username), id, status);
    }

    @Override
    @Transactional
    public void delete(String username, Long id) {
        dataSourceService.delete(normalizeUser(username), id);
    }

    private String normalizeUser(String username) {
        return (username == null || username.isBlank()) ? "anonymous" : username;
    }
}


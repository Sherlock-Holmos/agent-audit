package com.audit.data.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
/**
 * 数据源管理服务：处理数据库/文件数据源的创建、校验、对象发现与文件落盘。
 */
public class DataSourceService implements IDataSourceService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_FILE_EXT = Set.of("xlsx", "xls", "csv", "json", "txt");

    private final JdbcTemplate jdbcTemplate;
    private final Path uploadRoot;

    public DataSourceService(
        JdbcTemplate jdbcTemplate,
        @Value("${app.datasource.upload-dir:../../data/uploads}") String uploadDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        ensureDbPasswordColumn();
    }

    public List<Map<String, Object>> list(String ownerUsername, String keyword, String type, String status) {
        ensureSeed(ownerUsername);
        String sql = """
            SELECT id, name, type, db_type, host, port, database_name, username, file_name, file_size,
                   file_path, preview_rows, status, remark, created_at, updated_at
              FROM data_source_record
             WHERE owner_username = ?
             ORDER BY id DESC
            """;

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, i) -> toView(rs), ownerUsername);
        return rows.stream()
            .filter(item -> isBlank(keyword) || matchKeyword(item, keyword))
            .filter(item -> isBlank(type) || type.equalsIgnoreCase(String.valueOf(item.get("type"))))
            .filter(item -> isBlank(status) || status.equalsIgnoreCase(String.valueOf(item.get("status"))))
            .toList();
    }

    public Map<String, Object> createDatabase(String ownerUsername, Map<String, Object> payload) {
        String name = text(payload.get("name"));
        String dbType = text(payload.get("dbType"));
        String host = text(payload.get("host"));
        Integer port = toInt(payload.get("port"));
        String databaseName = text(payload.get("databaseName"));
        String username = text(payload.get("username"));
        String password = text(payload.get("password"));
        String remark = text(payload.get("remark"));

        if (isBlank(name) || isBlank(dbType) || isBlank(host) || port == null || isBlank(databaseName) || isBlank(username) || isBlank(password)) {
            throw new IllegalArgumentException("鏁版嵁搴撴暟鎹簮蹇呭～椤圭己澶");
        }

        String normalizedDbType = dbType.toUpperCase();
        if (!Set.of("MYSQL", "POSTGRESQL", "ORACLE", "SQLSERVER").contains(normalizedDbType)) {
            throw new IllegalArgumentException("鏆備笉鏀寔鐨勬暟鎹簱绫诲瀷: " + dbType);
        }

        ensureDriverAvailable(normalizedDbType);

        String jdbcUrl = buildJdbcUrl(normalizedDbType, host, port, databaseName);
        testDatabaseConnection(jdbcUrl, username, password);

        String now = now();
        String sql = """
            INSERT INTO data_source_record(
              owner_username, name, type, db_type, host, port, database_name, username, db_password,
              file_name, file_size, file_path, preview_rows, status, remark, created_at, updated_at
                        ) VALUES (?, ?, 'DATABASE', ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, 'ENABLED', ?, ?, ?)
            """;

        Long id = insertWithGeneratedId(sql, ps -> {
            ps.setString(1, ownerUsername);
            ps.setString(2, name);
            ps.setString(3, normalizedDbType);
            ps.setString(4, host);
            ps.setInt(5, port);
            ps.setString(6, databaseName);
            ps.setString(7, username);
            ps.setString(8, password);
            ps.setString(9, remark);
            ps.setString(10, now);
            ps.setString(11, now);
        });

        return getById(ownerUsername, id);
    }

    public Map<String, Object> createFile(String ownerUsername, String name, String remark, MultipartFile file) {
        if (isBlank(name)) throw new IllegalArgumentException("鏁版嵁婧愬悕绉颁笉鑳戒负绌");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("涓婁紶鏂囦欢涓嶈兘涓虹┖");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("涓婁紶鏂囦欢涓嶈兘瓒呰繃20MB");

        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "unknown");
        String fileExt = getFileExt(fileName);
        if (!ALLOWED_FILE_EXT.contains(fileExt)) throw new IllegalArgumentException("浠呮敮鎸?csv/xls/xlsx/json/txt 鏂囦欢");

        String safeFileName = sanitizeFileName(fileName);
        String storedName = ownerUsername + "_" + Instant.now().toEpochMilli() + "_" + safeFileName;

        Path savedFile;
        try {
            Files.createDirectories(uploadRoot);
            savedFile = uploadRoot.resolve(storedName);
            file.transferTo(Objects.requireNonNull(savedFile));
        } catch (Exception ex) {
            throw new IllegalArgumentException("鏂囦欢淇濆瓨澶辫触: " + ex.getMessage());
        }

        int previewRows = detectPreviewRows(savedFile, fileExt);
        String now = now();

        String sql = """
            INSERT INTO data_source_record(
              owner_username, name, type, db_type, host, port, database_name, username,
              file_name, file_size, file_path, preview_rows, status, remark, created_at, updated_at
                        ) VALUES (?, ?, 'FILE', NULL, NULL, NULL, NULL, NULL, ?, ?, ?, ?, 'ENABLED', ?, ?, ?)
            """;

        Long id = insertWithGeneratedId(sql, ps -> {
            ps.setString(1, ownerUsername);
            ps.setString(2, name);
            ps.setString(3, fileName);
            ps.setLong(4, file.getSize());
            ps.setString(5, savedFile.toString());
            ps.setInt(6, previewRows);
            ps.setString(7, remark);
            ps.setString(8, now);
            ps.setString(9, now);
        });

        return getById(ownerUsername, id);
    }

    public Map<String, Object> updateStatus(String ownerUsername, Long id, String status) {
        if (!"ENABLED".equalsIgnoreCase(status) && !"DISABLED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("鐘舵€佷粎鏀寔 ENABLED 鎴?DISABLED");
        }

        int count = jdbcTemplate.update(
            "UPDATE data_source_record SET status=?, updated_at=? WHERE owner_username=? AND id=?",
            status.toUpperCase(), now(), ownerUsername, id
        );
        if (count == 0) throw new IllegalArgumentException("鏁版嵁婧愪笉瀛樺湪");
        return getById(ownerUsername, id);
    }

    public void delete(String ownerUsername, Long id) {
        Map<String, Object> source = getById(ownerUsername, id);
        String sourceType = text(source.get("type"));
        if ("FILE".equalsIgnoreCase(sourceType)) {
            deleteUploadedFileIfExists(text(source.get("filePath")));
        }

        int count = jdbcTemplate.update("DELETE FROM data_source_record WHERE owner_username=? AND id=?", ownerUsername, id);
        if (count == 0) throw new IllegalArgumentException("鏁版嵁婧愪笉瀛樺湪");
    }

    public List<Map<String, Object>> listSourceObjects(String ownerUsername, Long id) {
        Map<String, Object> item = getById(ownerUsername, id);
        String type = String.valueOf(item.get("type"));

        if ("DATABASE".equalsIgnoreCase(type)) {
            Map<String, Object> dbConfig = getDatabaseConfig(ownerUsername, id);
            String dbType = text(dbConfig.get("dbType")).toUpperCase();
            String jdbcUrl = buildJdbcUrl(
                dbType,
                text(dbConfig.get("host")),
                toInt(dbConfig.get("port")),
                text(dbConfig.get("databaseName"))
            );

            List<String> tables = fetchTablesFromDatabase(
                jdbcUrl,
                text(dbConfig.get("username")),
                text(dbConfig.get("password")),
                text(dbConfig.get("databaseName")),
                dbType
            );
            List<Map<String, Object>> objects = new ArrayList<>();
            for (String table : tables) {
                Map<String, Object> o = new HashMap<>();
                o.put("sourceId", id);
                o.put("sourceName", item.get("name"));
                o.put("sourceType", "DATABASE");
                o.put("objectType", "TABLE");
                o.put("objectName", table);
                o.put("label", item.get("name") + " / " + table);
                objects.add(o);
            }
            return objects;
        }

        Map<String, Object> o = new HashMap<>();
        String fileName = text(item.get("fileName"));
        o.put("sourceId", id);
        o.put("sourceName", item.get("name"));
        o.put("sourceType", "FILE");
        o.put("objectType", "FILE");
        o.put("objectName", isBlank(fileName) ? "uploaded_file" : fileName);
        o.put("label", item.get("name") + " / " + (isBlank(fileName) ? "uploaded_file" : fileName));
        return List.of(o);
    }

    private void ensureSeed(String ownerUsername) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM data_source_record WHERE owner_username=?", Integer.class, ownerUsername
        );
        if (count != null && count > 0) return;

        String now = now();
        jdbcTemplate.update(
            """
            INSERT INTO data_source_record(
              owner_username, name, type, db_type, host, port, database_name, username,
              file_name, file_size, file_path, preview_rows, status, remark, created_at, updated_at
                        ) VALUES (?, '审计演示库', 'DATABASE', 'MYSQL', '127.0.0.1', 3306, 'audit_demo', 'audit_user',
                                            NULL, NULL, NULL, NULL, 'ENABLED', '系统初始化', ?, ?)
            """,
            ownerUsername, now, now
        );
    }

    private Map<String, Object> getById(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            """
            SELECT id, name, type, db_type, host, port, database_name, username, file_name, file_size,
                   file_path, preview_rows, status, remark, created_at, updated_at
              FROM data_source_record
             WHERE owner_username=? AND id=?
            """,
            (rs, i) -> toView(rs), ownerUsername, id
        );
        if (rows.isEmpty()) throw new IllegalArgumentException("鏁版嵁婧愪笉瀛樺湪");
        return rows.get(0);
    }

    private Map<String, Object> toView(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> view = new HashMap<>();
        view.put("id", rs.getLong("id"));
        view.put("name", nvl(rs.getString("name")));
        view.put("type", nvl(rs.getString("type")));
        view.put("dbType", nvl(rs.getString("db_type")));
        view.put("host", nvl(rs.getString("host")));
        view.put("port", rs.getObject("port") == null ? 0 : rs.getInt("port"));
        view.put("databaseName", nvl(rs.getString("database_name")));
        view.put("username", nvl(rs.getString("username")));
        view.put("fileName", nvl(rs.getString("file_name")));
        view.put("fileSize", rs.getObject("file_size") == null ? 0L : rs.getLong("file_size"));
        view.put("filePath", nvl(rs.getString("file_path")));
        view.put("previewRows", rs.getObject("preview_rows") == null ? 0 : rs.getInt("preview_rows"));
        view.put("status", nvl(rs.getString("status")));
        view.put("remark", nvl(rs.getString("remark")));
        view.put("createdAt", formatDateTime(rs.getObject("created_at")));
        view.put("updatedAt", formatDateTime(rs.getObject("updated_at")));
        return view;
    }

    private Long insertWithGeneratedId(String sql, SqlSetter setter) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = Objects.requireNonNull(
                con.prepareStatement(Objects.requireNonNull(sql), Statement.RETURN_GENERATED_KEYS)
            );
            setter.accept(Objects.requireNonNull(ps));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("鍒涘缓璁板綍澶辫触");
        return key.longValue();
    }

    private static boolean matchKeyword(Map<String, Object> item, String keyword) {
        String lower = keyword.toLowerCase();
        return List.of("name", "host", "databaseName", "fileName").stream()
            .map(item::get)
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .anyMatch(v -> v.toLowerCase().contains(lower));
    }

    private static Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
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

    private static String formatDateTime(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime time) {
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return String.valueOf(value);
    }

    private static String getFileExt(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) return "";
        return fileName.substring(idx + 1).toLowerCase();
    }

    private static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void deleteUploadedFileIfExists(String filePath) {
        if (isBlank(filePath)) return;

        Path target = Paths.get(filePath).toAbsolutePath().normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("鏂囦欢璺緞闈炴硶锛屾嫆缁濆垹闄");
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new IllegalStateException("鏂囦欢鍒犻櫎澶辫触: " + ex.getMessage(), ex);
        }
    }

    private void testDatabaseConnection(String jdbcUrl, String username, String password) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            if (!connection.isValid(5)) {
                throw new IllegalStateException("鏁版嵁搴撹繛鎺ユ祴璇曞け璐ワ紝璇锋鏌ュ湴鍧€涓庤处鍙蜂俊鎭");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("鏁版嵁搴撹繛鎺ュけ璐? " + ex.getMessage());
        }
    }

    private static String buildJdbcUrl(String dbType, String host, Integer port, String databaseName) {
        if (port == null) {
            throw new IllegalArgumentException("鏁版嵁搴撶鍙ｄ笉鑳戒负绌");
        }
        return switch (dbType) {
            case "MYSQL" -> "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=5000&socketTimeout=5000";
            case "POSTGRESQL" -> "jdbc:postgresql://" + host + ":" + port + "/" + databaseName
                + "?connectTimeout=5&socketTimeout=5";
            case "SQLSERVER" -> "jdbc:sqlserver://" + host + ":" + port
                + ";databaseName=" + databaseName + ";encrypt=true;trustServerCertificate=true;loginTimeout=5";
            case "ORACLE" -> "jdbc:oracle:thin:@//" + host + ":" + port + "/" + databaseName;
            default -> throw new IllegalArgumentException("鏆備笉鏀寔鐨勬暟鎹簱绫诲瀷: " + dbType);
        };
    }

    private Map<String, Object> getDatabaseConfig(String ownerUsername, Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            """
            SELECT db_type, host, port, database_name, username, db_password
              FROM data_source_record
             WHERE owner_username=? AND id=? AND type='DATABASE'
            """,
            (rs, i) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("dbType", nvl(rs.getString("db_type")));
                map.put("host", nvl(rs.getString("host")));
                map.put("port", rs.getObject("port") == null ? null : rs.getInt("port"));
                map.put("databaseName", nvl(rs.getString("database_name")));
                map.put("username", nvl(rs.getString("username")));
                map.put("password", nvl(rs.getString("db_password")));
                return map;
            },
            ownerUsername,
            id
        );

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("鏁版嵁搴撴暟鎹簮涓嶅瓨鍦");
        }
        return rows.get(0);
    }

    private List<String> fetchTablesFromDatabase(
        String jdbcUrl,
        String username,
        String password,
        String databaseName,
        String dbType
    ) {
        List<String> tables = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = null;
            String schemaPattern = null;

            if ("MYSQL".equalsIgnoreCase(dbType)) {
                catalog = databaseName;
            }
            if ("POSTGRESQL".equalsIgnoreCase(dbType)) {
                schemaPattern = "public";
            }

            try (ResultSet rs = metaData.getTables(catalog, schemaPattern, "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!isBlank(tableName)) {
                        tables.add(tableName);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("璇诲彇鏁版嵁搴撳璞″け璐? " + ex.getMessage());
        }

        if (tables.isEmpty()) {
            throw new IllegalStateException("杩炴帴鎴愬姛锛屼絾鏈鍙栧埌鍙敤鏁版嵁琛");
        }
        return tables;
    }

    private void ensureDriverAvailable(String dbType) {
        String driverClass = switch (dbType) {
            case "MYSQL" -> "com.mysql.cj.jdbc.Driver";
            case "POSTGRESQL" -> "org.postgresql.Driver";
            case "ORACLE" -> "oracle.jdbc.OracleDriver";
            case "SQLSERVER" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default -> null;
        };
        if (driverClass == null) {
            return;
        }
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("褰撳墠鏈嶅姟鏈畨瑁?" + dbType + " JDBC 椹卞姩锛岃鑱旂郴绠＄悊鍛");
        }
    }

    private void ensureDbPasswordColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE data_source_record ADD COLUMN db_password VARCHAR(512)");
        } catch (DataAccessException ex) {
            if (!isDuplicateColumnError(ex)) {
                throw ex;
            }
        }
    }

    private boolean isDuplicateColumnError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("duplicate column") ||
                    normalized.contains("already exists") ||
                    normalized.contains("column already exists")) {
                    return true;
                }
            }

            if (current instanceof SQLException sqlEx) {
                if (sqlEx.getErrorCode() == 1060) {
                    return true;
                }
                String sqlState = sqlEx.getSQLState();
                if ("42S21".equalsIgnoreCase(sqlState)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static int detectPreviewRows(Path filePath, String ext) {
        try {
            return switch (ext) {
                case "csv" -> countCsvRows(filePath);
                case "xlsx" -> countXlsxRows(filePath);
                default -> 0;
            };
        } catch (Exception ex) {
            return 0;
        }
    }

    private static int countCsvRows(Path filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            int rows = 0;
            while (reader.readLine() != null && rows < 10000) rows++;
            return rows;
        }
    }

    private static int countXlsxRows(Path filePath) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(filePath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return countOccurrences(xml, "<row ");
                }
            }
        }
        return 0;
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }

    private static String now() {
        return DATE_TIME_FORMATTER.format(Instant.now());
    }

    @FunctionalInterface
    private interface SqlSetter {
        void accept(PreparedStatement ps) throws java.sql.SQLException;
    }
}


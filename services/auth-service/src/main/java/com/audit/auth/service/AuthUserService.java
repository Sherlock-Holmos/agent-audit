package com.audit.auth.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthUserService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final String DEFAULT_ROLE = "AUDITOR";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureProfileColumns();
    }

    public Map<String, Object> register(String username, String password) {
        ensureDefaultAdmin();
        if (isBlank(username) || isBlank(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM auth_user WHERE username=?",
            Integer.class,
            username
        );
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        Timestamp now = now();
        jdbcTemplate.update(
            "INSERT INTO auth_user(username,password_hash,nickname,role,status,created_at,updated_at) VALUES(?,?,?, ?, 'ENABLED', ?, ?)",
            username,
            passwordEncoder.encode(password),
            username,
            DEFAULT_ROLE,
            now,
            now
        );

        return getProfileByUsername(username);
    }

    public Map<String, Object> authenticate(String username, String password) {
        ensureDefaultAdmin();
        if (isBlank(username) || isBlank(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,username,password_hash,nickname,avatar_url,email,phone,department,role,status,last_login_at FROM auth_user WHERE username=?",
            (rs, i) -> row(rs),
            username
        );
        if (rows.isEmpty()) {
            throw new IllegalStateException("用户名或密码错误");
        }

        Map<String, Object> user = rows.get(0);
        if (!"ENABLED".equalsIgnoreCase(String.valueOf(user.get("status")))) {
            throw new IllegalStateException("用户已停用");
        }

        String passwordHash = String.valueOf(user.get("passwordHash"));
        if (!passwordEncoder.matches(password, passwordHash)) {
            throw new IllegalStateException("用户名或密码错误");
        }

        Timestamp loginAt = now();
        jdbcTemplate.update("UPDATE auth_user SET last_login_at=?, updated_at=? WHERE id=?", loginAt, loginAt, user.get("id"));

        Map<String, Object> out = new HashMap<>();
        out.put("id", String.valueOf(user.get("id")));
        out.put("username", user.get("username"));
        out.put("nickname", user.get("nickname"));
        out.put("avatarUrl", user.get("avatarUrl"));
        out.put("email", user.get("email"));
        out.put("phone", user.get("phone"));
        out.put("department", user.get("department"));
        out.put("role", user.get("role"));
        out.put("lastLoginAt", formatDateTime(loginAt));
        return out;
    }

    public Map<String, Object> getProfileByUsername(String username) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT id,username,nickname,avatar_url,email,phone,department,role,status,last_login_at FROM auth_user WHERE username=?",
            (rs, i) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", String.valueOf(rs.getLong("id")));
                map.put("username", rs.getString("username"));
                map.put("nickname", nvl(rs.getString("nickname")));
                map.put("avatarUrl", nvl(rs.getString("avatar_url")));
                map.put("email", nvl(rs.getString("email")));
                map.put("phone", nvl(rs.getString("phone")));
                map.put("department", nvl(rs.getString("department")));
                map.put("role", rs.getString("role"));
                map.put("status", rs.getString("status"));
                map.put("lastLoginAt", formatDateTime(rs.getTimestamp("last_login_at")));
                return map;
            },
            username
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("用户不存在");
        }
        Map<String, Object> user = rows.get(0);
        if (!"ENABLED".equalsIgnoreCase(String.valueOf(user.get("status")))) {
            throw new IllegalStateException("用户已停用");
        }
        user.remove("status");
        return user;
    }

    public Map<String, Object> updateProfile(String username, Map<String, Object> payload) {
        String nickname = text(payload.get("nickname"));
        String avatarUrl = text(payload.get("avatarUrl"));
        String email = text(payload.get("email"));
        String phone = text(payload.get("phone"));
        String department = text(payload.get("department"));

        if (avatarUrl.length() > 8_000_000) {
            throw new IllegalArgumentException("头像图片过大，请压缩后重试");
        }

        if (isBlank(nickname)) {
            nickname = username;
        }

        int updated;
        try {
            updated = jdbcTemplate.update(
                "UPDATE auth_user SET nickname=?, avatar_url=?, email=?, phone=?, department=?, updated_at=? WHERE username=?",
                nickname,
                avatarUrl,
                email,
                phone,
                department,
                now(),
                username
            );
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("头像图片过大，请压缩后重试");
        }
        if (updated == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
        return getProfileByUsername(username);
    }

    public void deactivate(String username) {
        int updated = jdbcTemplate.update(
            "UPDATE auth_user SET status='DISABLED', updated_at=? WHERE username=?",
            now(),
            username
        );
        if (updated == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    private void ensureDefaultAdmin() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM auth_user", Integer.class);
        if (count != null && count > 0) return;
        Timestamp now = now();
        jdbcTemplate.update(
            "INSERT INTO auth_user(username,password_hash,nickname,role,status,created_at,updated_at) VALUES('admin',?,?,?, 'ENABLED', ?, ?)",
            passwordEncoder.encode("admin123"),
            "管理员",
            "ADMIN",
            now,
            now
        );
    }

    private Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rs.getLong("id"));
        map.put("username", rs.getString("username"));
        map.put("passwordHash", rs.getString("password_hash"));
        map.put("nickname", nvl(rs.getString("nickname")));
        map.put("avatarUrl", nvl(rs.getString("avatar_url")));
        map.put("email", nvl(rs.getString("email")));
        map.put("phone", nvl(rs.getString("phone")));
        map.put("department", nvl(rs.getString("department")));
        map.put("role", rs.getString("role"));
        map.put("status", rs.getString("status"));
        map.put("lastLoginAt", formatDateTime(rs.getTimestamp("last_login_at")));
        return map;
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private static String formatDateTime(Timestamp ts) {
        if (ts == null) return "";
        return DATE_TIME_FORMATTER.format(ts.toInstant());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private void ensureProfileColumns() {
        addColumnIfMissing("ALTER TABLE auth_user ADD COLUMN nickname VARCHAR(128)");
        addColumnIfMissing("ALTER TABLE auth_user ADD COLUMN avatar_url TEXT");
        addColumnIfMissing("ALTER TABLE auth_user ADD COLUMN email VARCHAR(255)");
        addColumnIfMissing("ALTER TABLE auth_user ADD COLUMN phone VARCHAR(64)");
        addColumnIfMissing("ALTER TABLE auth_user ADD COLUMN department VARCHAR(128)");
        ensureAvatarColumnLongText();
    }

    private void ensureAvatarColumnLongText() {
        try {
            jdbcTemplate.execute("ALTER TABLE auth_user MODIFY COLUMN avatar_url LONGTEXT");
        } catch (DataAccessException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (!msg.contains("doesn't exist") && !msg.contains("unknown column")) {
                throw ex;
            }
        }
    }

    private void addColumnIfMissing(String sql) {
        try {
            jdbcTemplate.execute(Objects.requireNonNull(sql));
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
                if (normalized.contains("duplicate column") || normalized.contains("already exists")) {
                    return true;
                }
            }
            if (current instanceof SQLException sqlException) {
                if (sqlException.getErrorCode() == 1060) {
                    return true;
                }
                String sqlState = sqlException.getSQLState();
                if ("42S21".equalsIgnoreCase(sqlState)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}

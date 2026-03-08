package com.audit.auth.controller;

import com.audit.auth.service.AuthUserService;
import com.audit.auth.service.JwtService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthUserService authUserService;
    private final JwtService jwtService;

    public AuthController(AuthUserService authUserService, JwtService jwtService) {
        this.authUserService = authUserService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String username = request.getOrDefault("username", "").trim();
        String password = request.getOrDefault("password", "").trim();

        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", "用户名和密码不能为空"
            ));
        }
        try {
            Map<String, Object> user = authUserService.register(username, password);
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "注册成功",
                "data", user
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", 409,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.getOrDefault("username", "").trim();
        String password = request.getOrDefault("password", "").trim();

        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", "用户名和密码不能为空"
            ));
        }
        try {
            Map<String, Object> userInfo = authUserService.authenticate(username, password);
            String token = jwtService.generateToken(username, String.valueOf(userInfo.get("role")));
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "登录成功",
                "data", Map.of(
                    "token", token,
                    "user", userInfo
                )
            ));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "code", 401,
                "message", ex.getMessage()
            ));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String username = resolveUsername(authorization);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", 401, "message", "未登录"));
        }
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "ok",
                "data", authUserService.getProfileByUsername(username)
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", 401, "message", ex.getMessage()));
        }
    }

    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateMe(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody Map<String, Object> payload
    ) {
        String username = resolveUsername(authorization);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", 401, "message", "未登录"));
        }
        try {
            return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "更新成功",
                "data", authUserService.updateProfile(username, payload)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/me")
    public ResponseEntity<Map<String, Object>> deactivateMe(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String username = resolveUsername(authorization);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", 401, "message", "未登录"));
        }
        authUserService.deactivate(username);
        return ResponseEntity.ok(Map.of("code", 0, "message", "账号已注销"));
    }

    private String resolveUsername(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7);
        try {
            return jwtService.parseUsername(token);
        } catch (Exception ex) {
            return null;
        }
    }
}

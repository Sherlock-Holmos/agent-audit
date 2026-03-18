package com.audit.auth.application;

import com.audit.auth.service.IAuthUserService;
import com.audit.auth.service.IJwtService;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService implements IAuthApplicationService {

    private final IAuthUserService authUserService;
    private final IJwtService jwtService;

    public AuthApplicationService(IAuthUserService authUserService, IJwtService jwtService) {
        this.authUserService = authUserService;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public Map<String, Object> register(String username, String password) {
        return authUserService.register(username, password);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> login(String username, String password) {
        Map<String, Object> userInfo = authUserService.authenticate(username, password);
        String token = jwtService.generateToken(username, String.valueOf(userInfo.get("role")));
        return Map.of(
            "token", token,
            "user", userInfo
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getProfile(String username) {
        return authUserService.getProfileByUsername(username);
    }

    @Override
    @Transactional
    public Map<String, Object> updateProfile(String username, Map<String, Object> payload) {
        return authUserService.updateProfile(username, payload);
    }

    @Override
    @Transactional
    public void deactivate(String username) {
        authUserService.deactivate(username);
    }

    @Override
    public String resolveUsername(String authorization) {
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

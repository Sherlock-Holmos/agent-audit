package com.audit.auth.application;

import java.util.Map;

public interface IAuthApplicationService {

    Map<String, Object> register(String username, String password);

    Map<String, Object> login(String username, String password);

    Map<String, Object> getProfile(String username);

    Map<String, Object> updateProfile(String username, Map<String, Object> payload);

    void deactivate(String username);

    String resolveUsername(String authorization);
}

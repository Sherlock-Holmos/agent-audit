package com.audit.gateway.application;

public class GatewayAuthContext {

    private final String username;
    private final String role;

    public GatewayAuthContext(String username, String role) {
        this.username = username;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}

package com.usth.edu.vn.Jwt;

public final class JwtIssuerType {
    public static final String DASHBOARD_USER = "dashboard-user";

    /** Audience expected by dashboard-user ({@code mp.jwt.verify.audiences}). */
    public static final String AUDIENCE = "dashboard-api";

    private JwtIssuerType() {
    }
}

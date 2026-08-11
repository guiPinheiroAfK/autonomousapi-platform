package com.autonomousapi.core.auth.dto;

/** Par de tokens devolvido no signup/login/refresh. */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds) {

    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
    }
}

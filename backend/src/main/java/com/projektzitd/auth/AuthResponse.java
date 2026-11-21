package com.projektzitd.auth;

/**
 * Generic authentication response DTO.
 *
 * @param authenticated  true when session/token is fully established
 * @param totpRequired   true when password was correct but TOTP code still needed
 * @param message        human-readable status / username after full auth
 * @param token          JWT token issued upon successful authentication
 */
public record AuthResponse(boolean authenticated, boolean totpRequired, String message, String token) {}

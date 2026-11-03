package com.projektzitd.auth;

/**
 * Stub interface representing the JWT integration point.
 * TODO: Replace with Ryczan's JWT implementation when ready.
 *
 * Possible integration: JwtPort bean provided by the jwt package,
 * injected into UserController / TotpController to issue tokens
 * instead of (or alongside) HTTP sessions.
 */
public interface JwtPort {

    /** Generate a signed JWT for the given username. */
    String generateToken(String username);

    /** Validate token and return username, or throw if invalid/expired. */
    String validateToken(String token);
}

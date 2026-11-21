package com.projektzitd.auth;

/**
 * Interface representing the JWT integration point.
 * Implemented by JwtService.
 */
public interface JwtPort {
    String generateToken(String username);
    String validateToken(String token);
}
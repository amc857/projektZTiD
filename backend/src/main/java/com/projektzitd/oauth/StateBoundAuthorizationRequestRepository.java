package com.projektzitd.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the pending OAuth2 request between the redirect to GitHub and the callback.
 *
 * The session cookie is SameSite=Strict, so it isn't sent on the callback from github.com.
 * Instead of weakening the session:
 *  - server keeps: state → (random nonce, request), valid for 5 min,
 *  - browser gets the nonce in an HttpOnly, SameSite=Lax cookie (callback path only),
 *  - on callback, state and nonce must match (same browser, blocks login-CSRF),
 *  - each entry is one-time use.
 */
public class StateBoundAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "OAUTH2_BINDING";
    private static final String COOKIE_PATH = "/login/oauth2/code";
    private static final Duration TTL = Duration.ofMinutes(5);

    private record Pending(String binding, OAuth2AuthorizationRequest request, Instant expiresAt) {}

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Callback from GitHub: checks state and the nonce cookie. */
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) return null;

        Pending p = pending.get(state);
        if (p == null || Instant.now().isAfter(p.expiresAt())) return null;

        String binding = readBindingCookie(request);
        if (binding == null || !MessageDigest.isEqual(
                binding.getBytes(StandardCharsets.UTF_8),
                p.binding().getBytes(StandardCharsets.UTF_8))) {
            return null; // started in a different browser → reject
        }
        return p.request();
    }

    /** Redirect to GitHub: saves the request and sets the nonce cookie. */
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            clearCookie(response);
            return;
        }
        pending.values().removeIf(p -> Instant.now().isAfter(p.expiresAt())); // clean up expired

        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        String binding = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);

        pending.put(authorizationRequest.getState(),
                new Pending(binding, authorizationRequest, Instant.now().plus(TTL)));
        setCookie(response, binding, TTL);
    }

    /** After use: removes the entry and the cookie (one-time). */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        if (authorizationRequest != null) {
            pending.remove(authorizationRequest.getState());
        }
        clearCookie(response);
        return authorizationRequest;
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    private static String readBindingCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static void setCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .sameSite("Lax") // sent on the redirect back from github.com
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static void clearCookie(HttpServletResponse response) {
        setCookie(response, "", Duration.ZERO);
    }
}
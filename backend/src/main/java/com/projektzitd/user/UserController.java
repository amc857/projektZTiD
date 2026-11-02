package com.projektzitd.user;

import com.projektzitd.auth.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * Handles registration and password-based login.
 * Login flow with TOTP enabled:
 *   1. POST /api/auth/login — verify password → set TOTP_PENDING in session
 *   2. POST /api/totp/verify — verify TOTP code → promote session to fully authenticated
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    // ── Registration ───────────────────────────────────────────────────────────

    record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 100) String password) {}

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest req) {
        try {
            userService.register(req.username(), req.password());
            return ResponseEntity.status(HttpStatus.CREATED).body("User registered");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {}

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req,
                                              HttpServletRequest httpRequest) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(false, true, "Bad credentials"));
        }

        User user = userService.getByUsername(req.username());

        if (user.isTotpEnabled()) {
            // Password OK but TOTP still required — store pending state in session
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("TOTP_PENDING", req.username());
            return ResponseEntity.ok(new AuthResponse(false, true, "TOTP required"));
        }

        // No TOTP — fully authenticate now
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        return ResponseEntity.ok(new AuthResponse(true, false, "Login successful"));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out");
    }

    // ── Current user info ─────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }
        return ResponseEntity.ok(new AuthResponse(true, false, auth.getName()));
    }
}

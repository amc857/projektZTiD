package com.projektzitd.totp;

import com.projektzitd.auth.AuthResponse;
import com.projektzitd.user.User;
import com.projektzitd.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller exposing TOTP setup and verification endpoints.
 *
 * Setup flow  (authenticated user):
 *   GET  /api/totp/setup   → returns { secret, otpauthUri }
 *   POST /api/totp/enable  → { code } — verifies first OTP, activates 2FA
 *   POST /api/totp/disable → { code } — verifies OTP, deactivates 2FA
 *
 * Login flow  (unauthenticated, TOTP_PENDING session attribute set by /api/auth/login):
 *   POST /api/totp/verify  → { code } — verifies OTP, promotes session to authenticated
 */
@RestController
@RequestMapping("/api/totp")
@RequiredArgsConstructor
public class TotpController {

    private final TotpService totpService;
    private final UserService userService;

    // ── Setup: generate secret + URI ─────────────────────────────────────────

    @GetMapping("/setup")
    public ResponseEntity<?> setup(Principal principal) {
        String username = principal.getName();
        String secret = totpService.generateAndStoreSecret(username);
        String uri = totpService.buildOtpauthUri(username, secret);
        return ResponseEntity.ok(Map.of("secret", secret, "otpauthUri", uri));
    }

    // ── Enable TOTP (verify first code) ──────────────────────────────────────

    record CodeRequest(String code) {}

    @PostMapping("/enable")
    public ResponseEntity<String> enable(@RequestBody CodeRequest req, Principal principal) {
        boolean ok = totpService.enable(principal.getName(), req.code());
        if (!ok) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid TOTP code");
        }
        return ResponseEntity.ok("TOTP enabled");
    }

    // ── Verify code during login (TOTP_PENDING session state) ─────────────────

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@RequestBody CodeRequest req,
                                               HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(false, true, "No active session"));
        }

        String pendingUser = (String) session.getAttribute("TOTP_PENDING");
        if (pendingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(false, true, "No pending TOTP verification"));
        }

        boolean ok = totpService.verifyCode(pendingUser, req.code());
        if (!ok) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(false, true, "Invalid TOTP code"));
        }

        // Code correct — promote session to fully authenticated
        session.removeAttribute("TOTP_PENDING");

        User user = userService.getByUsername(pendingUser);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                pendingUser, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(user.getRole())));
        SecurityContextHolder.getContext().setAuthentication(auth);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        return ResponseEntity.ok(new AuthResponse(true, false, "Login successful"));
    }

    // ── Disable TOTP ──────────────────────────────────────────────────────────

    @PostMapping("/disable")
    public ResponseEntity<String> disable(@RequestBody CodeRequest req, Principal principal) {
        boolean ok = totpService.disable(principal.getName(), req.code());
        if (!ok) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid TOTP code or TOTP not enabled");
        }
        return ResponseEntity.ok("TOTP disabled");
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<?> status(Principal principal) {
        User user = userService.getByUsername(principal.getName());
        return ResponseEntity.ok(Map.of("totpEnabled", user.isTotpEnabled()));
    }
}

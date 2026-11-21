package com.projektzitd.user;

import com.projektzitd.auth.AuthResponse;
import com.projektzitd.auth.JwtPort;
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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtPort jwtPort;

    record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 100) String password) {}

    // Bezpieczne DTO bez hasła i totpSecret
    public record UserDto(Long id, String username, String role, boolean totpEnabled) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getRole(), u.isTotpEnabled());
        }
    }

    // Endpoint pod przyszłą walidację na żywo w inpucie "Share"
    @GetMapping("/exists")
    public Map<String, Boolean> checkUserExists(@RequestParam String username) {
        boolean exists = username != null && !username.isBlank()
                && userService.existsByUsername(username.trim());
        return Map.of("exists", exists);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest req) {
        try {
            userService.register(req.username(), req.password());
            return ResponseEntity.status(HttpStatus.CREATED).body("User registered");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

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
                    .body(new AuthResponse(false, true, "Bad credentials", null));
        }

        User user = userService.getByUsername(req.username());

        if (user.isTotpEnabled()) {
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("TOTP_PENDING", req.username());
            return ResponseEntity.ok(new AuthResponse(false, true, "TOTP required", null));
        }

        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        String token = jwtPort.generateToken(req.username());
        return ResponseEntity.ok(new AuthResponse(true, false, "Login successful", token));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }
        return ResponseEntity.ok(new AuthResponse(true, false, auth.getName(), null));
    }

    //RBAC
    // RBAC: lista wszystkich użytkowników (tylko ADMIN)
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> getAllUsers() {
        return userService.getAllUsers().stream()
                .map(UserDto::from)
                .toList();
    }

    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            User updated = userService.updateUserRole(id, body.get("role"));
            return ResponseEntity.ok(UserDto.from(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

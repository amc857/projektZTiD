package com.projektzitd.user;

import com.projektzitd.auth.AuthResponse;
import com.projektzitd.auth.JwtPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtPort jwtPort;
    private final UserRepository userRepository;

    record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 100) String password) {}

    record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {}

    public record UserDto(Long id, String username, String role, boolean totpEnabled) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getRole(), u.isTotpEnabled());
        }
    }

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

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req,
                                              HttpServletRequest httpRequest) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(false, false, "Bad credentials", null, null));
        }

        User user = userService.getByUsername(req.username());

        if (user.isTotpEnabled()) {
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("TOTP_PENDING", req.username());
            return ResponseEntity.ok(new AuthResponse(false, true, "TOTP required", null, user.getRole()));
        }

        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        String token = jwtPort.generateToken(req.username());
        return ResponseEntity.ok(new AuthResponse(true, false, "Login successful", token, user.getRole()));
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
        User user = userService.getByUsername(auth.getName());
        return ResponseEntity.ok(UserDto.from(user));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            User updated = userService.updateOwnAccount(
                    auth.getName(),
                    body.get("username"),
                    body.get("password")
            );
            String newToken = jwtPort.generateToken(updated.getUsername());
            return ResponseEntity.ok(Map.of(
                    "user", UserDto.from(updated),
                    "token", newToken
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/admin/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> searchUsers(@RequestParam(defaultValue = "") String q) {
        String query = q.trim();
        if (query.isEmpty()) {
            return ResponseEntity.ok(Map.of("tooMany", false, "count", 0, "users", List.of()));
        }

        long total = userRepository.countByUsernameContainingIgnoreCase(query);
        if (total > 10) {
            return ResponseEntity.ok(Map.of(
                    "tooMany", true,
                    "count", total,
                    "users", List.of()
            ));
        }

        List<UserDto> matches = userRepository
                .findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc(query)
                .stream()
                .map(UserDto::from)
                .toList();

        return ResponseEntity.ok(Map.of(
                "tooMany", false,
                "count", total,
                "users", matches
        ));
    }

    @PutMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminUpdateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            User updated = userService.adminUpdateUser(
                    id,
                    (String) body.get("username"),
                    (String) body.get("password"),
                    (String) body.get("role"),
                    (Boolean) body.get("disableTotp")
            );
            return ResponseEntity.ok(UserDto.from(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
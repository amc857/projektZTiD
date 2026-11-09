package com.projektzitd.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User entity — stores credentials and TOTP state.
 * Fields totpSecret / totpEnabled are managed exclusively by TotpService.
 */
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 3, max = 50)
    @Column(unique = true, nullable = false)
    private String username;

    @NotBlank
    @Column(nullable = false)
    private String password; // BCrypt hash

    /** Role for RBAC (TODO: integrate with Czuchra's module). */
    @Column(nullable = false)
    private String role = "ROLE_USER";

    // ── TOTP fields ────────────────────────────────────────────────────────────

    /** Base32-encoded TOTP shared secret (null until /api/totp/setup called). */
    @Column(name = "totp_secret")
    private String totpSecret;

    /** True after user verified first code via /api/totp/enable. */
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    
    // ── OAuth 2.0 (GitHub) ────────────────────────────────────────────────────

    /**
     * Stable numeric GitHub user id (null for local accounts).
     * We link by id, not login - GitHub logins can be renamed and re-used.
     */
    @Column(name = "github_id", unique = true)
    private Long githubId;

}

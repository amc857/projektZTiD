package com.projektzitd.user;

import com.projektzitd.file.FileEntity;
import com.projektzitd.file.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;



@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileRepository fileRepository;

    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_USER", "ROLE_ADMIN");

    /** Called by Spring Security during form-based authentication. */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole()))
        );
    }

    /** Registers a new user with BCrypt-hashed password. */
    @Transactional
    public User register(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    /** Returns the local account linked to a GitHub id, creating it on first login. */
    @Transactional
    public User findOrCreateGithubUser(long githubId, String githubLogin) {
        return userRepository.findByGithubId(githubId).orElseGet(() -> {
            String username = githubLogin;
            for (int i = 2; userRepository.existsByUsername(username); i++) {
                username = githubLogin + "_gh" + i;
            }
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setGithubId(githubId);
            return userRepository.save(user);
        });
    }

    /** Loads the User entity (not just UserDetails) for TOTP operations. */
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User updateUserRole(Long userId, String newRole, String actingUsername) {
        if (newRole == null || !ALLOWED_ROLES.contains(newRole)) {
            throw new IllegalArgumentException("Invalid role. Allowed: " + ALLOWED_ROLES);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        guardSelfDemotion(user, newRole, actingUsername);
        user.setRole(newRole);
        return userRepository.save(user);
    }

    @Transactional
    public User updateOwnAccount(String currentUsername, String newUsername, String newPassword) {
        User user = getByUsername(currentUsername);

        if (newUsername != null && !newUsername.isBlank() && !newUsername.trim().equals(user.getUsername())) {
            changeUsername(user, newUsername.trim());
        }

        if (newPassword != null && !newPassword.isBlank()) {
            validatePassword(newPassword);
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        return userRepository.save(user);
    }

    @Transactional
    public User adminUpdateUser(Long userId, String newUsername, String newPassword, String role,
                                boolean disableTotp, String actingUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika"));

        if (role != null && !ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("Nieprawidłowa rola. Dozwolone: " + ALLOWED_ROLES);
        }
        if (role != null) {
            guardSelfDemotion(user, role, actingUsername);
        }

        if (newUsername != null && !newUsername.isBlank() && !newUsername.trim().equals(user.getUsername())) {
            if (user.getUsername().equals(actingUsername)) {
                throw new IllegalArgumentException(
                        "Własną nazwę zmień w zakładce \"Ustawienia mojego konta\"");
            }
            changeUsername(user, newUsername.trim());
        }
        if (newPassword != null && !newPassword.isBlank()) {
            validatePassword(newPassword);
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        if (role != null) {
            user.setRole(role);
        }
        if (disableTotp) {
            user.setTotpEnabled(false);
            user.setTotpSecret(null);
        }

        return userRepository.save(user);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Admin nie może odebrać sobie roli ADMIN (ochrona przed utratą dostępu do panelu). */
    private void guardSelfDemotion(User target, String newRole, String actingUsername) {
        if (target.getUsername().equals(actingUsername)
                && "ROLE_ADMIN".equals(target.getRole())
                && !"ROLE_ADMIN".equals(newRole)) {
            throw new IllegalArgumentException("Nie możesz odebrać sobie roli administratora");
        }
    }

    private void validatePassword(String password) {
        if (password.length() < 8 || password.length() > 100) {
            throw new IllegalArgumentException("Hasło musi mieć od 8 do 100 znaków");
        }
    }

    /**
     * Zmienia nazwę użytkownika i aktualizuje listy udostępnień plików
     * (sharedWith przechowuje nazwy użytkowników jako tekst).
     */
    private void changeUsername(User user, String newUsername) {
        if (newUsername.length() < 3 || newUsername.length() > 50) {
            throw new IllegalArgumentException("Nazwa użytkownika musi mieć od 3 do 50 znaków");
        }
        if (userRepository.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("Taka nazwa użytkownika jest już zajęta");
        }
        String oldUsername = user.getUsername();
        user.setUsername(newUsername);

        for (FileEntity file : fileRepository.findAll()) {
            String shared = file.getSharedWith();
            if (shared == null || shared.isBlank()) continue;
            List<String> names = Arrays.asList(shared.split(","));
            if (names.contains(oldUsername)) {
                file.setSharedWith(names.stream()
                        .map(n -> n.equals(oldUsername) ? newUsername : n)
                        .collect(Collectors.joining(",")));
                fileRepository.save(file);
            }
        }
    }
}

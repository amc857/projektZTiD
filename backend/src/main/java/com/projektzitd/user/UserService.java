package com.projektzitd.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Set;



@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
    public User updateUserRole(Long userId, String newRole) {
        if (newRole == null || !ALLOWED_ROLES.contains(newRole)) {
            throw new IllegalArgumentException("Invalid role. Allowed: " + ALLOWED_ROLES);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        user.setRole(newRole);
        return userRepository.save(user);
    }

    public User updateOwnAccount(String currentUsername, String newUsername, String newPassword) {
        User user = getByUsername(currentUsername);

        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(user.getUsername())) {
            if (newUsername.trim().length() < 3) {
                throw new IllegalArgumentException("Nazwa użytkownika musi mieć min. 3 znaki");
            }
            if (userRepository.existsByUsername(newUsername.trim())) {
                throw new IllegalArgumentException("Taka nazwa użytkownika jest już zajęta");
            }
            user.setUsername(newUsername.trim());
        }

        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 8) {
                throw new IllegalArgumentException("Hasło musi mieć min. 8 znaków");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        return userRepository.save(user);
    }

    public User adminUpdateUser(Long userId, String newUsername, String newPassword, String role, Boolean disableTotp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono użytkownika"));

        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(user.getUsername())) {
            if (userRepository.existsByUsername(newUsername.trim())) {
                throw new IllegalArgumentException("Nazwa użytkownika jest już zajęta");
            }
            user.setUsername(newUsername.trim());
        }
        if (newPassword != null && !newPassword.isBlank()) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        if (role != null && (role.equals("ROLE_USER") || role.equals("ROLE_ADMIN"))) {
            user.setRole(role);
        }
        if (Boolean.TRUE.equals(disableTotp)) {
            user.setTotpEnabled(false);
            user.setTotpSecret(null);
        }

        return userRepository.save(user);
    }
}

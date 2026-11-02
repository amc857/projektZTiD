package com.projektzitd.config;

import com.projektzitd.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with default cost factor 10
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for REST API — mitigated by SameSite=Strict cookie (set in properties)
            // and the fact that all state-changing ops require JSON body (not form-submittable)
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                // TOTP verification during login is accessible with only TOTP_PENDING session
                .requestMatchers(HttpMethod.POST, "/api/totp/verify").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Allow H2 console frames in dev
            .headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()))

            .authenticationProvider(authenticationProvider());

        return http.build();
    }
}

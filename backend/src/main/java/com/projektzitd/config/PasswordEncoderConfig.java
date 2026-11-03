package com.projektzitd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Separate config for PasswordEncoder to break the circular dependency:
 * SecurityConfig -> UserService -> PasswordEncoder (was in SecurityConfig).
 * Now: PasswordEncoderConfig -> standalone bean, no cycle.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // default cost factor 10
    }
}

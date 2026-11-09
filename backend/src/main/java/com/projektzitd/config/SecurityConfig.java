package com.projektzitd.config;

import com.projektzitd.user.UserService;
import com.projektzitd.oauth.GithubOAuth2UserService;
import com.projektzitd.oauth.StateBoundAuthorizationRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder; // injected from PasswordEncoderConfig — no cycle
    private final GithubOAuth2UserService githubOAuth2UserService;

    @Value("${app.frontend.url}")
    private String frontendUrl;


    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
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

                        // OAuth 2.0 — "Log in with GitHub" (Sarwa)
            .oauth2Login(oauth -> oauth
                // SameSite=Strict-safe storage of the pending request (see class docs)
                .authorizationEndpoint(ae -> ae
                    .authorizationRequestRepository(new StateBoundAuthorizationRequestRepository()))
                // Map GitHub identity → local User account
                .userInfoEndpoint(ui -> ui.userService(githubOAuth2UserService))
                .defaultSuccessUrl(frontendUrl + "/files", true)
                .failureUrl(frontendUrl + "/login?error=github")
            )

            // REST API: answer 401 instead of redirecting XHR calls to GitHub
            // (oauth2Login would otherwise make GitHub the default entry point)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))



            // Allow H2 console frames in dev
            .headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()))

            .authenticationProvider(authenticationProvider());

        return http.build();
    }
}

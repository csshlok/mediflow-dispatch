package org.example.hospital.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF so we can send POST/PATCH requests safely
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // Open all endpoints (temporarily for Phase 2)
                );

        return http.build();
    }
}
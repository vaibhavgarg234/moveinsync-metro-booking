package com.moveinsync.metro.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth

                // ✅ Static frontend files
                .requestMatchers(
                    "/", "/index.html", "/*.html", "/*.js", "/*.css", "/*.png",
                    "/static/**", "/css/**", "/js/**", "/images/**", "/favicon.ico"
                ).permitAll()

                // ✅ Health + Actuator
                .requestMatchers("/health", "/actuator/**").permitAll()

                // ✅ Auth (register / login)
                .requestMatchers("/api/auth/**").permitAll()

                // ✅ Swagger / OpenAPI
                .requestMatchers(
                    "/docs", "/docs/**", "/swagger-ui/**",
                    "/swagger-ui.html", "/v3/api-docs/**", "/api-docs/**"
                ).permitAll()

                // ✅ H2 console
                .requestMatchers("/h2-console/**").permitAll()

                // ✅ All metro endpoints open (demo mode)
                .requestMatchers("/api/metro/**").permitAll()

                // ✅ Bookings open (demo mode)
                .requestMatchers("/api/bookings/**").permitAll()

                // 🔒 Everything else requires auth
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(h -> h.frameOptions(f -> f.disable()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
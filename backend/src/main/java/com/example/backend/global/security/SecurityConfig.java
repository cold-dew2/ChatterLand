package com.example.backend.global.security;

import com.example.backend.global.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Configuration
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final String allowedOrigins;
    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          @Value("${app.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.jwtFilter = jwtFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/auth/find-id", "/api/v1/auth/password-reset/**", "/api/v1/centers", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> {
                            response.setStatus(401);
                            response.setCharacterEncoding("UTF-8");
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}");
                        })
                        .accessDeniedHandler((request, response, error) -> {
                            response.setStatus(403);
                            response.setCharacterEncoding("UTF-8");
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}");
                        }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

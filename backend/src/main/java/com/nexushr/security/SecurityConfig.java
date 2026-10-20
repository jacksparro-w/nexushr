package com.nexushr.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexushr.auth.JwtAuthenticationFilter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
  @Bean
  PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterServletRegistration(
      JwtAuthenticationFilter jwt) {
    FilterRegistrationBean<JwtAuthenticationFilter> registration =
        new FilterRegistrationBean<>(jwt);
    // The JWT filter belongs in Spring Security's chain only. Registering the
    // @Component as a servlet filter as well can run it outside that chain.
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwt, ObjectMapper json)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .headers(headers -> headers.frameOptions(frame -> frame.deny())
            .contentTypeOptions(content -> {})
            .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
        .cors(cors -> {})
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint((request, response, exception) -> {
                  log.warn("Unauthenticated request {} {} (bearer header {})", request.getMethod(),
                      request.getRequestURI(), request.getHeader("Authorization") == null ? "missing" : "present");
                  writeSecurityError(json, response, HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI());
                }).accessDeniedHandler((request, response, exception) -> {
                  log.warn("Forbidden request {} {}", request.getMethod(), request.getRequestURI());
                  writeSecurityError(json, response, HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI());
                }))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/api/health",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/ws/notifications")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private static void writeSecurityError(ObjectMapper json, jakarta.servlet.http.HttpServletResponse response,
      HttpStatus status, String message, String path) throws java.io.IOException {
    response.setStatus(status.value());
    response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(response.getOutputStream(), Map.of("timestamp", Instant.now(), "status", status.value(),
        "error", status.getReasonPhrase(), "message", message, "path", path, "fieldErrors", Map.of()));
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.frontend-origin:http://localhost:5173}") String frontendOrigin) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(frontendOrigin));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}

package com.nexushr.auth;

import com.nexushr.auth.AuthDtos.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  private final boolean secureCookie;
  private final long refreshDays;

  public AuthController(
      AuthService auth,
      @Value("${app.security.cookie-secure:false}") boolean secureCookie,
      @Value("${app.security.refresh-token-days:14}") long refreshDays) {
    this.auth = auth;
    this.secureCookie = secureCookie;
    this.refreshDays = refreshDays;
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    IssuedTokens tokens = auth.login(request);
    setRefreshCookie(response, tokens.refreshToken());
    return ResponseEntity.ok(tokens.response());
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    IssuedTokens result = auth.refresh(readCookie(request));
    setRefreshCookie(response, result.refreshToken());
    return ResponseEntity.ok(result.response());
  }

  @PostMapping("/logout")
  public ResponseEntity<MessageResponse> logout(
      HttpServletRequest request, HttpServletResponse response) {
    auth.logout(readCookie(request));
    clearRefreshCookie(response);
    return ResponseEntity.ok(new MessageResponse("Logged out"));
  }

  @PostMapping("/accounts")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @SecurityRequirement(name = "bearerAuth")
  public ResponseEntity<UserResponse> create(
      @Valid @RequestBody CreateAccountRequest request,
      org.springframework.security.core.Authentication principal) {
    if (principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HR"))
        && request.roles() != null
        && request.roles().stream().anyMatch(role -> role == Role.ADMIN || role == Role.HR)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN,
          "HR may create employee or manager accounts only");
    }
    return ResponseEntity.status(201).body(auth.createAccount(request));
  }

  @GetMapping("/me")
  @SecurityRequirement(name = "bearerAuth")
  public UserResponse me(org.springframework.security.core.Authentication principal) {
    return auth.getUser(java.util.UUID.fromString(principal.getName()));
  }

  private String readCookie(HttpServletRequest request) {
    if (request.getCookies() == null) return null;
    for (var cookie : request.getCookies())
      if ("nexushr_refresh".equals(cookie.getName())) return cookie.getValue();
    return null;
  }

  private void setRefreshCookie(HttpServletResponse response, String token) {
    if (token == null) return;
    ResponseCookie cookie =
        ResponseCookie.from("nexushr_refresh", token)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite(secureCookie ? "None" : "Strict")
            .path("/api/auth")
            .maxAge(Duration.ofDays(refreshDays))
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private void clearRefreshCookie(HttpServletResponse response) {
    ResponseCookie cookie =
        ResponseCookie.from("nexushr_refresh", "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite(secureCookie ? "None" : "Strict")
            .path("/api/auth")
            .maxAge(Duration.ZERO)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}

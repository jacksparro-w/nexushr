package com.nexushr.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.nexushr.auth.AuthDtos.IssuedTokens;
import com.nexushr.auth.AuthDtos.LoginRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
  @Mock UserRepository users;
  @Mock RefreshTokenRepository refreshTokens;
  @Mock PasswordEncoder passwords;
  @Mock AuthenticationManager authenticationManager;
  private AuthService service;
  private TokenService tokenService;
  private UserAccount user;

  @BeforeEach
  void setUp() {
    tokenService = new TokenService("01234567890123456789012345678901", 15);
    service =
        new AuthService(users, refreshTokens, passwords, authenticationManager, tokenService, 14);
    user = mock(UserAccount.class);
    lenient().when(user.getId()).thenReturn(UUID.randomUUID());
    lenient().when(user.getEmail()).thenReturn("employee@example.com");
    lenient().when(user.getRoles()).thenReturn(Set.of(Role.EMPLOYEE));
  }

  @Test
  void validLoginIssuesAccessAndRefreshCredentials() {
    when(authenticationManager.authenticate(any()))
        .thenReturn(
            UsernamePasswordAuthenticationToken.authenticated(
                "employee@example.com", null, Set.of()));
    when(users.findByEmailIgnoreCase("employee@example.com")).thenReturn(Optional.of(user));

    IssuedTokens result =
        service.login(new LoginRequest("employee@example.com", "correct-password"));

    assertNotNull(result.response().accessToken());
    assertNotNull(result.refreshToken());
    assertEquals("employee@example.com", result.response().user().email());
    assertTrue(
        tokenService
            .parse(result.response().accessToken())
            .get("roles", java.util.List.class)
            .contains("EMPLOYEE"));
    verify(refreshTokens).save(any(RefreshToken.class));
  }

  @Test
  void invalidPasswordIsRejectedByAuthenticationManager() {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("Bad credentials"));
    assertThrows(
        BadCredentialsException.class,
        () -> service.login(new LoginRequest("employee@example.com", "wrong-password")));
    verifyNoInteractions(refreshTokens);
  }

  @Test
  void expiredRefreshTokenIsRejectedWithoutIssuingAnotherToken() {
    RefreshToken token = mock(RefreshToken.class);
    when(token.getExpiresAt()).thenReturn(Instant.now().minusSeconds(1));
    when(token.isRevoked()).thenReturn(false);
    when(refreshTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

    assertThrows(ResponseStatusException.class, () -> service.refresh("expired-refresh-token"));
    verify(refreshTokens, never()).save(any(RefreshToken.class));
  }

  @Test
  void reuseOfRotatedRefreshTokenRevokesAllUserTokens() {
    RefreshToken token = mock(RefreshToken.class);
    when(token.isRevoked()).thenReturn(true);
    when(token.getUser()).thenReturn(user);
    when(refreshTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

    assertThrows(
        ResponseStatusException.class, () -> service.refresh("already-used-refresh-token"));
    verify(refreshTokens).deleteAllByUserId(user.getId());
    verify(refreshTokens, never()).save(any(RefreshToken.class));
  }
}

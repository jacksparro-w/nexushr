package com.nexushr.auth;

import com.nexushr.auth.AuthDtos.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwords;
  private final AuthenticationManager authenticationManager;
  private final TokenService tokenService;
  private final SecureRandom random = new SecureRandom();
  private final long refreshDays;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwords,
      AuthenticationManager authenticationManager,
      TokenService tokenService,
      @Value("${app.security.refresh-token-days:14}") long refreshDays) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwords = passwords;
    this.authenticationManager = authenticationManager;
    this.tokenService = tokenService;
    this.refreshDays = refreshDays;
  }

  @Transactional
  public IssuedTokens login(LoginRequest request) {
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password()));
    UserAccount user = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
    return issue(user);
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public IssuedTokens refresh(String rawToken) {
    if (rawToken == null || rawToken.isBlank())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is required");
    RefreshToken old =
        refreshTokens
            .findByTokenHashForUpdate(hash(rawToken))
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
    if (old.isRevoked() || old.getExpiresAt().isBefore(Instant.now())) {
      if (old.isRevoked()) refreshTokens.deleteAllByUserId(old.getUser().getId());
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or reused");
    }
    UserAccount user = old.getUser();
    String replacement = newRefreshToken();
    old.revoke(hash(replacement));
    refreshTokens.save(
        new RefreshToken(user, hash(replacement), Instant.now().plusSeconds(refreshDays * 86400)));
    return new IssuedTokens(response(user), replacement);
  }

  @Transactional
  public void logout(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) return;
    refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.revoke(null));
  }

  @Transactional
  public UserResponse createAccount(CreateAccountRequest request) {
    if (users.existsByEmailIgnoreCase(request.email()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
    Set<Role> roles =
        request.roles() == null || request.roles().isEmpty()
            ? Set.of(Role.EMPLOYEE)
            : request.roles();
    UserAccount user =
        users.save(
            new UserAccount(request.email().trim(), passwords.encode(request.password()), roles));
    return view(user);
  }

  private IssuedTokens issue(UserAccount user) {
    String raw = newRefreshToken();
    refreshTokens.save(
        new RefreshToken(user, hash(raw), Instant.now().plusSeconds(refreshDays * 86400)));
    return new IssuedTokens(response(user), raw);
  }

  private TokenResponse response(UserAccount user) {
    return new TokenResponse(
        tokenService.createAccessToken(user),
        "Bearer",
        Instant.now().plusSeconds(tokenService.accessTokenSeconds()),
        view(user));
  }

  public UserResponse getUser(UUID id) {
    return users
        .findById(id)
        .map(this::view)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
  }

  private UserResponse view(UserAccount user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getRoles());
  }

  private String newRefreshToken() {
    byte[] bytes = new byte[48];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

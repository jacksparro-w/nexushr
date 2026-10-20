package com.nexushr.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {
  private AuthDtos() {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record CreateAccountRequest(
      @NotBlank @Email String email,
      @NotBlank @Size(min = 12, max = 128) String password,
      Set<Role> roles) {}

  public record IssuedTokens(TokenResponse response, String refreshToken) {}

  public record TokenResponse(
      String accessToken, String tokenType, Instant accessTokenExpiresAt, UserResponse user) {}

  public record UserResponse(UUID id, String email, Set<Role> roles) {}

  public record MessageResponse(String message) {}
}

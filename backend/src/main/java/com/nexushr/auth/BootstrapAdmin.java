package com.nexushr.auth;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements CommandLineRunner {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final String email;
  private final String password;

  public BootstrapAdmin(
      UserRepository users,
      PasswordEncoder passwords,
      @Value("${app.bootstrap.admin-email:}") String email,
      @Value("${app.bootstrap.admin-password:}") String password) {
    this.users = users;
    this.passwords = passwords;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(String... args) {
    if (email.isBlank() && password.isBlank()) return;
    if (email.isBlank() || password.length() < 12)
      throw new IllegalStateException(
          "Set both bootstrap admin email and a password of at least 12 characters");
    if (!users.existsByEmailIgnoreCase(email))
      users.save(new UserAccount(email, passwords.encode(password), Set.of(Role.ADMIN)));
  }
}

package vn.sportshop.config;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.sportshop.user.*;

@Component
public class AdminBootstrap implements CommandLineRunner {

  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final String email, password;

  public AdminBootstrap(
    UserRepository u,
    PasswordEncoder p,
    @Value("${app.admin-email}") String e,
    @Value("${app.admin-password}") String s
  ) {
    users = u;
    passwords = p;
    email = e.strip().toLowerCase(Locale.ROOT);
    password = s;
  }

  public void run(String... args) {
    if (email.isBlank()) return;
    if (
      password.length() < 12 ||
      password.length() > 64 ||
      password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
    ) throw new IllegalStateException("ADMIN_PASSWORD needs 12-64 characters");
    var existing = users.findByEmail(email);
    if (existing.isPresent()) {
      if (!existing.get().role.equals("ADMIN")) throw new IllegalStateException(
        "Admin email belongs to USER"
      );
      return;
    }
    var u = new AppUser();
    u.name = "Quản trị viên";
    u.email = email;
    u.passwordHash = passwords.encode(password);
    u.role = "ADMIN";
    users.save(u);
  }
}

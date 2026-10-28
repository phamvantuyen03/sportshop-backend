package vn.sportshop.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.sportshop.user.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;

  public AuthController(UserRepository u, PasswordEncoder p, JwtEncoder e) {
    users = u;
    passwords = p;
    encoder = e;
  }

  public record Register(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 64) String password
  ) {}

  public record Login(
    @NotBlank @Email String email,
    @NotBlank @Size(max = 64) String password
  ) {}

  public record UserView(Long id, String name, String email, String role) {}

  public record AuthResponse(String token, UserView user) {}

  static UserView view(AppUser u) {
    return new UserView(u.id, u.name, u.email, u.role);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody Register r) {
    validatePasswordBytes(r.password());
    String email = r.email().strip().toLowerCase(Locale.ROOT);
    if (users.findByEmail(email).isPresent()) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Email đã được đăng ký"
    );
    var u = new AppUser();
    u.name = r.name().strip();
    u.email = email;
    u.passwordHash = passwords.encode(r.password());
    u.role = "USER";
    return token(users.save(u));
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody Login r) {
    validatePasswordBytes(r.password());
    var u = users
      .findByEmail(r.email().strip().toLowerCase(Locale.ROOT))
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "Email hoặc mật khẩu không đúng"
        )
      );
    if (
      !passwords.matches(r.password(), u.passwordHash)
    ) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Email hoặc mật khẩu không đúng"
    );
    return token(u);
  }

  @GetMapping("/me")
  public UserView me(@AuthenticationPrincipal Jwt jwt) {
    return view(
      users
        .findById(Long.valueOf(jwt.getSubject()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
    );
  }

  private static void validatePasswordBytes(String value) {
    if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Mật khẩu tối đa 72 byte UTF-8"
      );
    }
  }

  private AuthResponse token(AppUser u) {
    var now = Instant.now();
    var claims = JwtClaimsSet.builder()
      .issuer("sportshop")
      .subject(u.id.toString())
      .issuedAt(now)
      .expiresAt(now.plusSeconds(3600))
      .claim("role", u.role)
      .build();
    return new AuthResponse(
      encoder
        .encode(
          JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(),
            claims
          )
        )
        .getTokenValue(),
      view(u)
    );
  }
}

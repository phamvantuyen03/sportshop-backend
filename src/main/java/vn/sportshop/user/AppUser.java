package vn.sportshop.user;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 100)
  public String name;

  @Column(nullable = false, unique = true, length = 254)
  public String email;

  @Column(name = "password_hash", nullable = false)
  public String passwordHash;

  @Column(nullable = false, length = 10)
  public String role;
}

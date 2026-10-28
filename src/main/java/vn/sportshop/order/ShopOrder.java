package vn.sportshop.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "shop_order")
public class ShopOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long userId;

  @Column(nullable = false, length = 100)
  public String recipient;

  @Column(nullable = false, length = 20)
  public String phone;

  @Column(nullable = false, length = 500)
  public String address;

  @Column(nullable = false, precision = 15, scale = 0)
  public BigDecimal total;

  @Column(nullable = false, length = 20)
  public String status;

  @Column(nullable = false)
  public Instant createdAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
  public List<OrderItem> items = new ArrayList<>();
}

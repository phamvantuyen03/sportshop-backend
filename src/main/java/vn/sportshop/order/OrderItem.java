package vn.sportshop.order;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id")
  public ShopOrder order;

  @Column(nullable = false)
  public Long productId;

  @Column(nullable = false, length = 150)
  public String productName;

  @Column(nullable = false, precision = 15, scale = 0)
  public BigDecimal unitPrice;

  @Column(nullable = false)
  public int quantity;
}

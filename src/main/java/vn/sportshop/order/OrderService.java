package vn.sportshop.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.sportshop.product.*;

@Service
public class OrderService {

  private final OrderRepository orders;
  private final ProductRepository products;

  public OrderService(OrderRepository o, ProductRepository p) {
    orders = o;
    products = p;
  }

  public record Line(
    @NotNull @Positive Long productId,
    @Min(1) @Max(99) int quantity
  ) {}

  public record Checkout(
    @NotBlank @Size(max = 100) String recipient,
    @NotBlank @Pattern(regexp = "[+0-9 ()-]{8,20}") String phone,
    @NotBlank @Size(max = 500) String address,
    @NotEmpty @Size(max = 50) List<@NotNull @Valid Line> items
  ) {}

  public record ItemView(
    Long productId,
    String name,
    BigDecimal unitPrice,
    int quantity
  ) {}

  public record View(
    Long id,
    String recipient,
    String phone,
    String address,
    BigDecimal total,
    String status,
    Instant createdAt,
    List<ItemView> items
  ) {}

  public record StatusInput(
    @NotBlank
    @Pattern(regexp = "CONFIRMED|SHIPPING|COMPLETED|CANCELLED")
    String status
  ) {}

  static View view(ShopOrder o) {
    return new View(
      o.id,
      o.recipient,
      o.phone,
      o.address,
      o.total,
      o.status,
      o.createdAt,
      o.items
        .stream()
        .map(i ->
          new ItemView(i.productId, i.productName, i.unitPrice, i.quantity)
        )
        .toList()
    );
  }

  @Transactional(readOnly = true)
  public List<View> list(Long userId) {
    return (
      userId == null
        ? orders.findAllByOrderByIdDesc()
        : orders.findByUserIdOrderByIdDesc(userId)
    )
      .stream()
      .map(OrderService::view)
      .toList();
  }

  @Transactional
  public View checkout(Long userId, Checkout r) {
    var quantities = new TreeMap<Long, Integer>();
    for (var line : r.items()) {
      int qty = quantities.merge(
        line.productId(),
        line.quantity(),
        Integer::sum
      );
      if (qty > 99) throw bad("Mỗi sản phẩm tối đa 99 đơn vị");
    }
    var o = new ShopOrder();
    o.userId = userId;
    o.recipient = r.recipient().strip();
    o.phone = r.phone();
    o.address = r.address().strip();
    o.status = "PENDING";
    o.createdAt = Instant.now();
    o.total = BigDecimal.ZERO;
    for (var entry : quantities.entrySet()) {
      var p = products
        .lockById(entry.getKey())
        .orElseThrow(() -> bad("Sản phẩm không tồn tại"));
      int qty = entry.getValue();
      if (!p.active || p.stock < qty) throw bad(
        "Sản phẩm " + p.name + " không đủ hàng"
      );
      p.stock -= qty;
      var i = new OrderItem();
      i.order = o;
      i.productId = p.id;
      i.productName = p.name;
      i.unitPrice = p.price;
      i.quantity = qty;
      o.items.add(i);
      o.total = o.total.add(p.price.multiply(BigDecimal.valueOf(qty)));
    }
    return view(orders.save(o));
  }

  @Transactional
  public View status(Long id, String next) {
    var o = orders
      .lockById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    var allowed = switch (o.status) {
      case "PENDING" -> Set.of("CONFIRMED", "CANCELLED");
      case "CONFIRMED" -> Set.of("SHIPPING", "CANCELLED");
      case "SHIPPING" -> Set.of("COMPLETED");
      default -> Set.<String>of();
    };
    if (!allowed.contains(next)) throw bad(
      "Không thể chuyển trạng thái đơn hàng"
    );
    if (next.equals("CANCELLED")) {
      o.items
        .stream()
        .sorted(Comparator.comparing(i -> i.productId))
        .forEach(i -> {
          var p = products.lockById(i.productId).orElseThrow();
          p.stock += i.quantity;
        });
    }
    o.status = next;
    return view(o);
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}

package vn.sportshop.order;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderController {

  private final OrderService service;

  public OrderController(OrderService s) {
    service = s;
  }

  @PostMapping("/api/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public OrderService.View checkout(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody OrderService.Checkout r
  ) {
    return service.checkout(Long.valueOf(jwt.getSubject()), r);
  }

  @GetMapping("/api/orders")
  public List<OrderService.View> mine(@AuthenticationPrincipal Jwt jwt) {
    return service.list(Long.valueOf(jwt.getSubject()));
  }

  @GetMapping("/api/admin/orders")
  public List<OrderService.View> all() {
    return service.list(null);
  }

  @PatchMapping("/api/admin/orders/{id}/status")
  public OrderService.View status(
    @PathVariable Long id,
    @Valid @RequestBody OrderService.StatusInput r
  ) {
    return service.status(id, r.status());
  }
}

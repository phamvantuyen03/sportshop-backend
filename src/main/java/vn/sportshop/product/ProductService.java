package vn.sportshop.product;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

  private final ProductRepository products;

  public ProductService(ProductRepository p) {
    products = p;
  }

  public record Input(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 60) String category,
    @NotBlank @Size(max = 2000) String description,
    @NotNull
    @DecimalMin("1")
    @Digits(integer = 12, fraction = 0)
    BigDecimal price,
    @Min(0) @Max(1000000) int stock,
    boolean active
  ) {}

  public record View(
    Long id,
    String name,
    String category,
    String description,
    BigDecimal price,
    int stock,
    boolean active
  ) {}

  public static View view(Product p) {
    return new View(
      p.id,
      p.name,
      p.category,
      p.description,
      p.price,
      p.stock,
      p.active
    );
  }

  public List<View> list(boolean admin) {
    return (
      admin ? products.findAll() : products.findByActiveTrueOrderByIdDesc()
    )
      .stream()
      .map(ProductService::view)
      .toList();
  }

  public View get(Long id) {
    return view(
      products
        .findById(id)
        .filter(p -> p.active)
        .orElseThrow(() ->
          new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy sản phẩm"
          )
        )
    );
  }

  @Transactional
  public View save(Long id, Input r) {
    var p =
      id == null
        ? new Product()
        : products
            .lockById(id)
            .orElseThrow(() ->
              new ResponseStatusException(HttpStatus.NOT_FOUND)
            );
    p.name = r.name().strip();
    p.category = r.category().strip();
    p.description = r.description().strip();
    p.price = r.price();
    p.stock = r.stock();
    p.active = r.active();
    return view(products.save(p));
  }

  @Transactional
  public void hide(Long id) {
    var p = products
      .lockById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    p.active = false;
  }
}

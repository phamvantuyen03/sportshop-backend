package vn.sportshop.product;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductController {

  private final ProductService service;

  public ProductController(ProductService s) {
    service = s;
  }

  @GetMapping("/api/products")
  public List<ProductService.View> list() {
    return service.list(false);
  }

  @GetMapping("/api/products/{id}")
  public ProductService.View get(@PathVariable Long id) {
    return service.get(id);
  }

  @GetMapping("/api/admin/products")
  public List<ProductService.View> admin() {
    return service.list(true);
  }

  @PostMapping("/api/admin/products")
  @ResponseStatus(HttpStatus.CREATED)
  public ProductService.View create(
    @Valid @RequestBody ProductService.Input r
  ) {
    return service.save(null, r);
  }

  @PutMapping("/api/admin/products/{id}")
  public ProductService.View update(
    @PathVariable Long id,
    @Valid @RequestBody ProductService.Input r
  ) {
    return service.save(id, r);
  }

  @DeleteMapping("/api/admin/products/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void hide(@PathVariable Long id) {
    service.hide(id);
  }
}

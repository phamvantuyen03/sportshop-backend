package vn.sportshop;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.sportshop.order.*;
import vn.sportshop.product.*;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:shoptest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.admin-email=admin@test.local",
    "app.admin-password=TestAdmin-12345",
    "app.jwt-secret=test-secret-at-least-thirty-two-bytes-long",
  }
)
@AutoConfigureMockMvc
class ShopIntegrationTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  ProductRepository products;

  @Autowired
  OrderRepository orders;

  @Autowired
  OrderService service;

  JsonNode call(MockHttpServletRequestBuilder request, int status)
    throws Exception {
    String body = mvc
      .perform(request)
      .andExpect(status().is(status))
      .andReturn()
      .getResponse()
      .getContentAsString();
    return body.isBlank() ? json.nullNode() : json.readTree(body);
  }

  MockHttpServletRequestBuilder body(
    MockHttpServletRequestBuilder req,
    Object data
  ) throws Exception {
    return req
      .contentType("application/json")
      .content(json.writeValueAsString(data));
  }

  JsonNode register() throws Exception {
    return call(
      body(
        post("/api/auth/register"),
        Map.of(
          "name",
          "Khách hàng",
          "email",
          UUID.randomUUID() + "@test.local",
          "password",
          "Password-123"
        )
      ),
      201
    );
  }

  String admin() throws Exception {
    return call(
      body(
        post("/api/auth/login"),
        Map.of("email", "admin@test.local", "password", "TestAdmin-12345")
      ),
      200
    )
      .get("token")
      .asText();
  }

  Product product(int stock) {
    var p = new Product();
    p.name = "Test product";
    p.category = "Fitness";
    p.description = "Integration test";
    p.price = new BigDecimal("100000");
    p.stock = stock;
    return products.save(p);
  }

  Map<String, Object> checkout(Long id, int qty) {
    return Map.of(
      "recipient",
      "Khách",
      "phone",
      "0901234567",
      "address",
      "123 Nguyễn Trãi, Hà Nội",
      "items",
      List.of(Map.of("productId", id, "quantity", qty))
    );
  }

  @Test
  void unicodePasswordOverBcryptByteLimitIsRejected() throws Exception {
    call(
      body(
        post("/api/auth/register"),
        Map.of(
          "name",
          "Test",
          "email",
          "unicode@test.local",
          "password",
          "ậ".repeat(30)
        )
      ),
      400
    );
  }

  @Test
  void publicCatalogAndProtectedEndpoints() throws Exception {
    call(get("/api/products"), 200);
    call(get("/api/orders"), 401);
    call(get("/api/admin/products"), 401);
  }

  @Test
  void registerCannotEscalateRoleAndPasswordIsNotReturned() throws Exception {
    var user = register();
    assertThat(user.get("user").get("role").asText()).isEqualTo("USER");
    assertThat(user.toString()).doesNotContain("passwordHash");
    call(
      get("/api/admin/products").header(
        "Authorization",
        "Bearer " + user.get("token").asText()
      ),
      403
    );
    call(
      body(
        post("/api/auth/register"),
        Map.of(
          "name",
          "Attacker",
          "email",
          "attack@test.local",
          "password",
          "Password-123",
          "role",
          "ADMIN"
        )
      ),
      400
    );
  }

  @Test
  void invalidLoginAndTamperedTokenAreRejected() throws Exception {
    call(
      body(
        post("/api/auth/login"),
        Map.of("email", "admin@test.local", "password", "wrong-password")
      ),
      401
    );
    call(
      get("/api/orders").header("Authorization", "Bearer invalid.token.value"),
      401
    );
  }

  @Test
  void adminCanManageProductsAndUserCannot() throws Exception {
    String token = admin();
    var data = Map.of(
      "name",
      "New ball",
      "category",
      "Bóng đá",
      "description",
      "Ball",
      "price",
      500000,
      "stock",
      10,
      "active",
      true
    );
    var created = call(
      body(
        post("/api/admin/products").header("Authorization", "Bearer " + token),
        data
      ),
      201
    );
    long id = created.get("id").asLong();
    call(
      body(
        put("/api/admin/products/" + id).header(
          "Authorization",
          "Bearer " + token
        ),
        data
      ),
      200
    );
    call(
      delete("/api/admin/products/" + id).header(
        "Authorization",
        "Bearer " + token
      ),
      204
    );
    call(get("/api/products/" + id), 404);
  }

  @Test
  void checkoutComputesPriceAndIsolatesOrders() throws Exception {
    var p = product(5);
    var a = register();
    String token = a.get("token").asText();
    var order = call(
      body(
        post("/api/orders").header("Authorization", "Bearer " + token),
        checkout(p.id, 2)
      ),
      201
    );
    assertThat(order.get("total").decimalValue()).isEqualByComparingTo(
      "200000"
    );
    assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(3);
    assertThat(
      call(
        get("/api/orders").header("Authorization", "Bearer " + token),
        200
      ).size()
    ).isEqualTo(1);
    var b = register();
    assertThat(
      call(
        get("/api/orders").header(
          "Authorization",
          "Bearer " + b.get("token").asText()
        ),
        200
      ).size()
    ).isZero();
  }

  @Test
  void insufficientStockRollsBackEntireCheckout() throws Exception {
    var first = product(5);
    var second = product(0);
    var user = register();
    var data = Map.of(
      "recipient",
      "Test",
      "phone",
      "0901234567",
      "address",
      "Hà Nội",
      "items",
      List.of(
        Map.of("productId", first.id, "quantity", 1),
        Map.of("productId", second.id, "quantity", 1)
      )
    );
    long before = orders.count();
    call(
      body(
        post("/api/orders").header(
          "Authorization",
          "Bearer " + user.get("token").asText()
        ),
        data
      ),
      400
    );
    assertThat(products.findById(first.id).orElseThrow().stock).isEqualTo(5);
    assertThat(orders.count()).isEqualTo(before);
  }

  @Test
  void cancellationRestoresStockOnlyOnce() throws Exception {
    var p = product(4);
    var user = register();
    var order = call(
      body(
        post("/api/orders").header(
          "Authorization",
          "Bearer " + user.get("token").asText()
        ),
        checkout(p.id, 2)
      ),
      201
    );
    String url = "/api/admin/orders/" + order.get("id").asLong() + "/status";
    String token = admin();
    call(
      body(
        patch(url).header("Authorization", "Bearer " + token),
        Map.of("status", "COMPLETED")
      ),
      400
    );
    call(
      body(
        patch(url).header("Authorization", "Bearer " + token),
        Map.of("status", "CANCELLED")
      ),
      200
    );
    assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(4);
    call(
      body(
        patch(url).header("Authorization", "Bearer " + token),
        Map.of("status", "CANCELLED")
      ),
      400
    );
    assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(4);
  }

  @Test
  void invalidQuantityAndClientPriceAreRejected() throws Exception {
    var p = product(4);
    var user = register();
    String token = user.get("token").asText();
    call(
      body(
        post("/api/orders").header("Authorization", "Bearer " + token),
        checkout(p.id, 0)
      ),
      400
    );
    var data = new HashMap<>(checkout(p.id, 1));
    data.put("total", 1);
    call(
      body(
        post("/api/orders").header("Authorization", "Bearer " + token),
        data
      ),
      400
    );
    assertThat(products.findById(p.id).orElseThrow().stock).isEqualTo(4);
  }

  @Test
  void concurrentBuyersCannotOversell() throws Exception {
    var p = product(1);
    long uid = register().get("user").get("id").asLong();
    var request = new OrderService.Checkout(
      "Test",
      "0901234567",
      "Hà Nội",
      List.of(new OrderService.Line(p.id, 1))
    );
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> buy = () -> {
        start.await();
        try {
          service.checkout(uid, request);
          return true;
        } catch (org.springframework.web.server.ResponseStatusException e) {
          return false;
        }
      };
      var a = pool.submit(buy);
      var b = pool.submit(buy);
      start.countDown();
      assertThat(
        List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))
      ).containsExactlyInAnyOrder(true, false);
    }
    assertThat(products.findById(p.id).orElseThrow().stock).isZero();
  }
}

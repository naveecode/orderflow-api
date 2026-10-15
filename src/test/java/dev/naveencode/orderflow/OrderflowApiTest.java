package dev.naveencode.orderflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderflowApiTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeAll
    void authenticateAdmin() throws Exception {
        adminToken = login("admin@orderflow.dev", "ChangeMe123!");
    }

    @Test
    void registersLogsInAndRejectsBadCredentials() throws Exception {
        String email = uniqueEmail("ada");
        var registered = read(mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "Password123", "fullName", "Ada Lovelace")))), 201);
        assertThat(registered.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(registered.get("user").get("role").asText()).isEqualTo("USER");

        var me = read(mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(registered.get("accessToken").asText()))), 200);
        assertThat(me.get("email").asText()).isEqualTo(email);

        var duplicate = read(problem(mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "Password123", "fullName", "Ada Again"))))), 409);
        assertThat(duplicate.get("code").asText()).isEqualTo("EMAIL_TAKEN");

        var invalid = read(problem(mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "WrongPassword1"))))), 401);
        assertThat(invalid.get("code").asText()).isEqualTo("UNAUTHORIZED");

        read(problem(mvc.perform(get("/api/v1/me"))), 401);
    }

    @Test
    void adminManagesProductsAndTheCatalogIsPublic() throws Exception {
        String sku = sku("LAMP");
        var created = read(mvc.perform(post("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, "Desk Lamp", "19.50", 8))), 201);
        assertThat(created.get("sku").asText()).isEqualTo(sku);
        UUID id = UUID.fromString(created.get("id").asText());

        var listed = read(mvc.perform(get("/api/v1/products").param("q", sku)), 200);
        assertThat(listed.get("content")).hasSize(1);
        assertThat(listed.get("content").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("19.50");

        read(mvc.perform(get("/api/v1/products/" + id)), 200);

        String user = registerUser("catalog");
        var forbidden = read(problem(mvc.perform(post("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku("NOPE"), "Nope", "1.00", 1)))), 403);
        assertThat(forbidden.get("code").asText()).isEqualTo("FORBIDDEN");

        var duplicate = read(problem(mvc.perform(post("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, "Desk Lamp", "19.50", 8)))), 409);
        assertThat(duplicate.get("code").asText()).isEqualTo("SKU_TAKEN");
    }

    @Test
    void createsAnOrderIdempotentlyAndWalksTheStatusWorkflow() throws Exception {
        UUID productId = createProduct(sku("KBD"), "129.00", 5);
        String user = registerUser("buyer");
        String key = UUID.randomUUID().toString();
        String body = orderJson(productId, 1);

        var created = mvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
        var order = read(created.andExpect(header().exists("Location")), 201);
        UUID orderId = UUID.fromString(order.get("id").asText());
        assertThat(order.get("status").asText()).isEqualTo("CREATED");
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("129.00");
        assertThat(order.get("items").get(0).get("sku").asText()).startsWith("OF-");
        assertThat(stock(productId)).isEqualTo(4);

        var replay = mvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
        var replayed = read(replay.andExpect(header().string("Idempotency-Replayed", "true")), 200);
        assertThat(replayed.get("id").asText()).isEqualTo(orderId.toString());
        assertThat(stock(productId)).isEqualTo(4);

        var conflict = read(problem(mvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(productId, 2)))), 409);
        assertThat(conflict.get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(conflict.get("type").asText()).contains("idempotency-conflict");

        var missingKey = read(problem(mvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))), 400);
        assertThat(missingKey.get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        read(mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "PAID")))), 200);

        var cannotShip = read(problem(mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "SHIPPED"))))), 403);
        assertThat(cannotShip.get("code").asText()).isEqualTo("FORBIDDEN");

        read(mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "SHIPPED")))), 200);
        var delivered = read(mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "DELIVERED")))), 200);
        assertThat(delivered.get("status").asText()).isEqualTo("DELIVERED");

        var terminal = read(problem(mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CANCELLED"))))), 409);
        assertThat(terminal.get("code").asText()).isEqualTo("INVALID_TRANSITION");
        assertThat(stock(productId)).isEqualTo(4);
    }

    @Test
    void cancelRestoresStockAndHidesOrdersFromOtherUsers() throws Exception {
        UUID productId = createProduct(sku("MSE"), "49.00", 10);
        String owner = registerUser("owner");
        String other = registerUser("other");
        var order = read(mvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(productId, 3))), 201);
        UUID orderId = UUID.fromString(order.get("id").asText());
        assertThat(stock(productId)).isEqualTo(7);

        var cancelled = read(mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "CANCELLED")))), 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(stock(productId)).isEqualTo(10);

        var hidden = read(problem(mvc.perform(get("/api/v1/orders/" + orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(other)))), 404);
        assertThat(hidden.get("code").asText()).isEqualTo("ORDER_NOT_FOUND");

        var asAdmin = read(mvc.perform(get("/api/v1/orders/" + orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))), 200);
        assertThat(asAdmin.get("status").asText()).isEqualTo("CANCELLED");

        var ownerPage = read(mvc.perform(get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .param("status", "CANCELLED")
                .param("size", "5")), 200);
        assertThat(ownerPage.get("content")).isNotEmpty();
        assertThat(ownerPage.get("content").get(0).get("userId").asText()).isEqualTo(order.get("userId").asText());

        var otherPage = read(mvc.perform(get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(other))
                .param("userId", order.get("userId").asText())), 200);
        assertThat(otherPage.get("totalElements").asInt()).isZero();
    }

    @Test
    void filtersOrdersByTimeAndRejectsBadInput() throws Exception {
        UUID productId = createProduct(sku("HUB"), "79.50", 4);
        String user = registerUser("pager");
        Instant before = Instant.now().minusSeconds(5);
        read(mvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(productId, 1))), 201);
        Instant after = Instant.now().plusSeconds(5);

        var page = read(mvc.perform(get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .param("createdFrom", before.toString())
                .param("createdTo", after.toString())
                .param("page", "0")
                .param("size", "1")
                .param("sort", "createdAt,desc")), 200);
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        assertThat(page.get("totalPages").asInt()).isEqualTo(1);

        var future = read(mvc.perform(get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .param("createdFrom", after.toString())), 200);
        assertThat(future.get("totalElements").asInt()).isZero();

        var badRange = read(problem(mvc.perform(get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .param("createdFrom", after.toString())
                .param("createdTo", before.toString()))), 400);
        assertThat(badRange.get("code").asText()).isEqualTo("VALIDATION_FAILED");

        var invalidBody = read(problem(mvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}"))), 400);
        assertThat(invalidBody.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(invalidBody.get("errors").get(0).get("field").asText()).isEqualTo("items");

        read(problem(mvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))), 400);

        read(problem(mvc.perform(get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .param("size", "500"))), 400);
    }

    @Test
    void rejectsOrdersThatStockCannotCover() throws Exception {
        UUID productId = createProduct(sku("ONE"), "10.00", 1);
        String user = registerUser("stock");
        var denied = read(problem(mvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(productId, 2)))), 409);
        assertThat(denied.get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(stock(productId)).isEqualTo(1);

        mvc.perform(put("/api/v1/products/" + productId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "name", "Retired",
                        "unitPrice", new BigDecimal("10.00"),
                        "stockQuantity", 5,
                        "active", false))))
                .andExpect(status().isOk());
        var unavailable = read(problem(mvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderJson(productId, 1)))), 409);
        assertThat(unavailable.get("code").asText()).isEqualTo("PRODUCT_UNAVAILABLE");
    }

    @Test
    void onlyOneConcurrentOrderConsumesTheLastUnit() throws Exception {
        UUID productId = createProduct(sku("RACE"), "3.00", 1);
        String user = registerUser("race");
        String body = orderJson(productId, 1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<Integer> attempt = () -> mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
        var first = pool.submit(attempt);
        var second = pool.submit(attempt);
        var statuses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)).stream()
                .sorted()
                .toList();
        pool.shutdown();

        assertThat(statuses).containsExactly(201, 409);
        assertThat(stock(productId)).isZero();
    }

    @Test
    void concurrentRetriesWithTheSameKeyCreateOneOrder() throws Exception {
        UUID productId = createProduct(sku("IDEM"), "4.00", 5);
        String user = registerUser("idem");
        String key = UUID.randomUUID().toString();
        String body = orderJson(productId, 1);
        var pool = Executors.newFixedThreadPool(2);
        Callable<JsonNode> attempt = () -> objectMapper.readTree(mvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getContentAsString());
        var first = pool.submit(attempt);
        var second = pool.submit(attempt);
        JsonNode left = first.get(20, TimeUnit.SECONDS);
        JsonNode right = second.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(left.get("id").asText()).isEqualTo(right.get("id").asText());
        assertThat(left.get("status").asText()).isEqualTo("CREATED");
        assertThat(stock(productId)).isEqualTo(4);
    }

    @Test
    void publishesHealthAndOpenApiWithoutAuth() throws Exception {
        var health = read(mvc.perform(get("/actuator/health/readiness")), 200);
        assertThat(health.get("status").asText()).isEqualTo("UP");

        var spec = read(mvc.perform(get("/v3/api-docs")), 200);
        assertThat(spec.get("paths").has("/api/v1/orders")).isTrue();
        assertThat(spec.get("components").get("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(spec.get("info").get("title").asText()).isEqualTo("orderflow-api");
    }

    private UUID createProduct(String sku, String price, int stock) throws Exception {
        var created = read(mvc.perform(post("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, sku, price, stock))), 201);
        return UUID.fromString(created.get("id").asText());
    }

    private int stock(UUID productId) throws Exception {
        return read(mvc.perform(get("/api/v1/products/" + productId)), 200).get("stockQuantity").asInt();
    }

    private String registerUser(String label) throws Exception {
        var response = read(mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", uniqueEmail(label),
                        "password", "Password123",
                        "fullName", label)))), 201);
        return response.get("accessToken").asText();
    }

    private String login(String email, String password) throws Exception {
        var response = read(mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password)))), 200);
        return response.get("accessToken").asText();
    }

    private String productJson(String sku, String name, String price, int stock) throws Exception {
        return json(Map.of(
                "sku", sku,
                "name", name,
                "description", name,
                "unitPrice", new BigDecimal(price),
                "stockQuantity", stock));
    }

    private String orderJson(UUID productId, int quantity) throws Exception {
        return json(Map.of("items", List.of(Map.of("productId", productId, "quantity", quantity))));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode read(ResultActions actions, int expectedStatus) throws Exception {
        var result = actions.andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions problem(ResultActions actions) throws Exception {
        return actions.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail(String label) {
        return label + "-" + UUID.randomUUID() + "@example.com";
    }

    private static String sku(String label) {
        return ("OF-" + label + "-" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase();
    }
}

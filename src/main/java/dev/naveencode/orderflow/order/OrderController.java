package dev.naveencode.orderflow.order;

import dev.naveencode.orderflow.common.CurrentUser;
import dev.naveencode.orderflow.common.PageResponse;
import dev.naveencode.orderflow.error.ApiException;
import dev.naveencode.orderflow.error.ErrorCode;
import dev.naveencode.orderflow.error.ProblemBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Place orders, page through them, and move them through the status workflow.")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9._:\\-]{8,128}$");

    private final OrderService orders;
    private final CurrentUser currentUser;

    public OrderController(OrderService orders, CurrentUser currentUser) {
        this.orders = orders;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(
            summary = "Create an order",
            description = """
                    Requires an Idempotency-Key header. Repeating the same key and body returns the original order
                    with Idempotency-Replayed: true. The same key with a different body returns 409.
                    Stock is decremented when the order is created.
                    """)
    @ApiResponse(responseCode = "201", description = "Order created")
    @ApiResponse(responseCode = "200", description = "Idempotent replay of an existing order")
    @ApiResponse(responseCode = "409", description = "Idempotency conflict, unavailable product, or insufficient stock",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemBody.class)))
    public ResponseEntity<OrderResponse> create(
            @Parameter(
                    name = IDEMPOTENCY_KEY,
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "Client-generated key, 8-128 characters (letters, digits, dot, underscore, colon, hyphen).",
                    example = "6f1e1b5a-2c3d-4e5f-8a9b-0c1d2e3f4a5b")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        validateKey(idempotencyKey);
        var outcome = orders.create(currentUser.require(), idempotencyKey, request);
        if (outcome.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotency-Replayed", "true")
                    .body(outcome.order());
        }
        return ResponseEntity.created(URI.create("/api/v1/orders/" + outcome.order().id()))
                .body(outcome.order());
    }

    @GetMapping
    @Operation(summary = "List orders", description = "Users see their own orders. Admins see every order and may filter by userId.")
    public PageResponse<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Inclusive lower bound, ISO-8601")
            @RequestParam(required = false) Instant createdFrom,
            @Parameter(description = "Exclusive upper bound, ISO-8601")
            @RequestParam(required = false) Instant createdTo,
            @Parameter(description = "Admin only. Ignored for USER.")
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "createdAt, status, or totalAmount. Direction asc or desc.")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return orders.list(currentUser.require(), status, createdFrom, createdTo, userId, page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order")
    @ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemBody.class)))
    public OrderResponse get(@PathVariable UUID id) {
        return orders.get(currentUser.require(), id);
    }

    @PostMapping("/{id}/transitions")
    @Operation(
            summary = "Move an order to a new status",
            description = """
                    Users may pay (CREATED to PAID) or cancel (CREATED or PAID to CANCELLED) their own orders.
                    Admins may apply any legal transition, including PAID to SHIPPED and SHIPPED to DELIVERED.
                    Cancelling restores stock.
                    """)
    @ApiResponse(responseCode = "409", description = "Transition is not legal for the current status",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemBody.class)))
    @ApiResponse(responseCode = "403", description = "Role cannot apply this transition",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemBody.class)))
    public ResponseEntity<OrderResponse> transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        var order = orders.transition(currentUser.require(), id, request.status());
        return ResponseEntity.status(HttpStatus.OK).body(order);
    }

    private static void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key header is required");
        }
        if (!KEY.matcher(idempotencyKey).matches()) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key must be 8-128 characters and use only letters, digits, '.', '_', ':', or '-'");
        }
    }
}

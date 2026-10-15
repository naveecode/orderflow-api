package dev.naveencode.orderflow.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed", "validation-failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request", "malformed-request"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency key required", "idempotency-key-required"),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "Invalid idempotency key", "idempotency-key-invalid"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized", "unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden", "forbidden"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found", "order-not-found"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found", "product-not-found"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "Email already registered", "email-taken"),
    SKU_TAKEN(HttpStatus.CONFLICT, "SKU already exists", "sku-taken"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Idempotency key conflict", "idempotency-conflict"),
    INVALID_TRANSITION(HttpStatus.CONFLICT, "Invalid order transition", "invalid-transition"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient stock", "insufficient-stock"),
    PRODUCT_UNAVAILABLE(HttpStatus.CONFLICT, "Product unavailable", "product-unavailable"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict", "conflict"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "internal-error");

    private static final String TYPE_BASE = "https://naveecode.github.io/orderflow-api/problems#";

    private final HttpStatus status;
    private final String title;
    private final String slug;

    ErrorCode(HttpStatus status, String title, String slug) {
        this.status = status;
        this.title = title;
        this.slug = slug;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String type() {
        return TYPE_BASE + slug;
    }

    public String slug() {
        return slug;
    }
}

package dev.naveencode.orderflow.order;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "CREATED, then PAID, SHIPPED, and DELIVERED. CREATED or PAID may be CANCELLED.")
public enum OrderStatus {
    CREATED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED
}

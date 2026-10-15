package dev.naveencode.orderflow.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TransitionRequest(
        @Schema(example = "PAID", description = "Next status. Allowed moves: CREATED to PAID or CANCELLED, PAID to SHIPPED or CANCELLED, SHIPPED to DELIVERED.")
        @NotNull(message = "Status is required")
        OrderStatus status
) {
}

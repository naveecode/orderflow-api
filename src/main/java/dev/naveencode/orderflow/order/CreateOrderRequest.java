package dev.naveencode.orderflow.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @Schema(description = "Lines to purchase. Each product may appear once.")
        @NotEmpty(message = "At least one item is required")
        @Size(max = 20, message = "An order can contain at most 20 items")
        List<@Valid Line> items
) {

    public record Line(
            @Schema(example = "22222222-2222-2222-2222-222222222201")
            @NotNull(message = "Product id is required")
            UUID productId,
            @Schema(example = "1")
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 100, message = "Quantity cannot exceed 100")
            Integer quantity
    ) {
    }
}

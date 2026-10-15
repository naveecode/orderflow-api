package dev.naveencode.orderflow.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @Schema(example = "OF-LAMP-01")
        @NotBlank(message = "SKU is required")
        @Pattern(regexp = "^[A-Z0-9-]{2,64}$", message = "SKU must be 2-64 characters using A-Z, 0-9, and hyphen")
        String sku,
        @Schema(example = "Desk Lamp")
        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,
        @Schema(example = "Warm light, dimmable.")
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,
        @Schema(example = "39.00")
        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.00", message = "Unit price cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Unit price must have at most 2 decimal places")
        BigDecimal unitPrice,
        @Schema(example = "25")
        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity cannot be negative")
        @Max(value = 1_000_000, message = "Stock quantity is too large")
        Integer stockQuantity
) {
}

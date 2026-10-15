package dev.naveencode.orderflow.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "Problem", description = "RFC 7807 problem detail")
public record ProblemBody(
        @Schema(example = "https://naveecode.github.io/orderflow-api/problems#invalid-transition")
        String type,
        @Schema(example = "Invalid order transition")
        String title,
        @Schema(example = "409")
        int status,
        @Schema(example = "Cannot transition order from DELIVERED to CANCELLED")
        String detail,
        @Schema(example = "/api/v1/orders/3f1c")
        String instance,
        @Schema(example = "INVALID_TRANSITION")
        String code,
        List<FieldError> errors
) {

    @Schema(name = "FieldError")
    public record FieldError(
            @Schema(example = "items[0].quantity") String field,
            @Schema(example = "Quantity must be at least 1") String message
    ) {
    }
}

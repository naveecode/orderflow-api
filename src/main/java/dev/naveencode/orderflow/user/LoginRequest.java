package dev.naveencode.orderflow.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "admin@orderflow.dev")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255)
        String email,
        @Schema(example = "ChangeMe123!")
        @NotBlank(message = "Password is required")
        @Size(max = 72)
        String password
) {
}

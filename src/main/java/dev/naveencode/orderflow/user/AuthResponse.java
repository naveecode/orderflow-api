package dev.naveencode.orderflow.user;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(example = "Bearer") String tokenType,
        String accessToken,
        @Schema(description = "Access token lifetime in seconds", example = "7200") long expiresIn,
        UserResponse user
) {
}

package dev.naveencode.orderflow.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record UserResponse(
        UUID id,
        @Schema(example = "ada@example.com") String email,
        @Schema(example = "Ada Lovelace") String fullName,
        Role role
) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(account.getId(), account.getEmail(), account.getFullName(), account.getRole());
    }
}

package dev.naveencode.orderflow.common;

import dev.naveencode.orderflow.error.ApiException;
import dev.naveencode.orderflow.error.ErrorCode;
import dev.naveencode.orderflow.security.AuthenticatedUser;
import dev.naveencode.orderflow.user.UserResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public AuthenticatedUser require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Authentication is required");
        }
        return user;
    }

    public UserResponse requireProfile() {
        var user = require();
        return new UserResponse(user.id(), user.email(), user.fullName(), user.role());
    }
}

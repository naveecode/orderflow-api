package dev.naveencode.orderflow.security;

import dev.naveencode.orderflow.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemHttpWriter problems;

    public SecurityProblemHandlers(ProblemHttpWriter problems) {
        this.problems = problems;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.core.AuthenticationException authException) {
        problems.write(request, response, ErrorCode.UNAUTHORIZED, "Authentication is required");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) {
        problems.write(request, response, ErrorCode.FORBIDDEN, "You do not have permission to perform this action");
    }
}

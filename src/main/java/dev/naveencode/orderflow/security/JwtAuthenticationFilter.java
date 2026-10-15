package dev.naveencode.orderflow.security;

import dev.naveencode.orderflow.error.ErrorCode;
import dev.naveencode.orderflow.user.UserAccountRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository users;
    private final ProblemHttpWriter problems;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository users, ProblemHttpWriter problems) {
        this.jwtService = jwtService;
        this.users = users;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            problems.write(request, response, ErrorCode.UNAUTHORIZED, "Invalid or expired token");
            return;
        }
        try {
            var parsed = jwtService.parse(token);
            var account = users.findById(parsed.userId()).orElse(null);
            if (account == null) {
                problems.write(request, response, ErrorCode.UNAUTHORIZED, "Invalid or expired token");
                return;
            }
            var principal = AuthenticatedUser.from(account);
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            problems.write(request, response, ErrorCode.UNAUTHORIZED, "Invalid or expired token");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

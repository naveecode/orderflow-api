package dev.naveencode.orderflow.user;

import dev.naveencode.orderflow.error.ApiException;
import dev.naveencode.orderflow.error.ErrorCode;
import dev.naveencode.orderflow.security.AuthenticatedUser;
import dev.naveencode.orderflow.security.JwtService;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String DUMMY_PASSWORD_HASH =
            "$2b$10$qffvRf9fVpt3goFEiCyDS.Dg44of5e70oBYJHl0j.yxxDt539r4PW";

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
        var account = new UserAccount(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                Role.USER);
        try {
            users.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
        return issue(account);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        var account = users.findByEmail(email).orElse(null);
        if (account == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }
        return issue(account);
    }

    private AuthResponse issue(UserAccount account) {
        var principal = AuthenticatedUser.from(account);
        return new AuthResponse("Bearer", jwtService.issue(principal), jwtService.expiresInSeconds(), UserResponse.from(account));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

package dev.naveencode.orderflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.naveencode.orderflow.user.Role;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long";
    private static final Instant ISSUED_AT = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void issuesATokenThatRoundTrips() {
        var service = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        var user = user();

        String token = service.issue(user);

        assertThat(service.parse(token).userId()).isEqualTo(user.id());
        assertThat(service.parse(token).email()).isEqualTo(user.email());
        assertThat(service.expiresInSeconds()).isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void rejectsAnExpiredToken() {
        var issued = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        String token = issued.issue(user());
        var later = service(Clock.fixed(ISSUED_AT.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        String token = service(Clock.fixed(ISSUED_AT, ZoneOffset.UTC)).issue(user());
        var other = new JwtService(SECRET + "-other-secret-value", Duration.ofMinutes(15), Clock.systemUTC());

        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAShortSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", Duration.ofMinutes(5), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private static JwtService service(Clock clock) {
        return new JwtService(SECRET, Duration.ofMinutes(15), clock);
    }

    private static AuthenticatedUser user() {
        return new AuthenticatedUser(UUID.randomUUID(), "ada@example.com", "Ada Lovelace", Role.USER);
    }
}

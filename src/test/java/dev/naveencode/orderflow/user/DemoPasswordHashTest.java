package dev.naveencode.orderflow.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DemoPasswordHashTest {

    @Test
    void seededAdminPasswordMatchesTheDocumentedDemoPassword() {
        var encoder = new BCryptPasswordEncoder(10);
        assertThat(encoder.matches(
                "ChangeMe123!",
                "$2b$10$xPy0x8qai5eeXrGTlPHaY.vmlQv8cETlgC6MBoxPDwCTe0fPhYyfK")).isTrue();
        assertThat(encoder.matches("ChangeMe123!", AuthService.DUMMY_PASSWORD_HASH)).isFalse();
    }
}

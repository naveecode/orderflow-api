package dev.naveencode.orderflow.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orderflow.jwt")
public record JwtProperties(String secret, Duration expiration) {
}

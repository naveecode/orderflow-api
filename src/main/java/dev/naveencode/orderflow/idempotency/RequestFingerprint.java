package dev.naveencode.orderflow.idempotency;

import dev.naveencode.orderflow.order.CreateOrderRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Collectors;

public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String hash(CreateOrderRequest request) {
        String canonical = request.items().stream()
                .sorted(Comparator.comparing(CreateOrderRequest.Line::productId))
                .map(line -> line.productId() + ":" + line.quantity())
                .collect(Collectors.joining("|", "v1|", ""));
        return sha256(canonical);
    }

    private static String sha256(String value) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

package dev.naveencode.orderflow.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.naveencode.orderflow.order.CreateOrderRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    @Test
    void ignoresItemOrderAndChangesWhenQuantityChanges() {
        UUID keyboard = UUID.fromString("22222222-2222-2222-2222-222222222201");
        UUID mouse = UUID.fromString("22222222-2222-2222-2222-222222222202");
        var first = new CreateOrderRequest(List.of(
                new CreateOrderRequest.Line(keyboard, 1),
                new CreateOrderRequest.Line(mouse, 2)));
        var reversed = new CreateOrderRequest(List.of(
                new CreateOrderRequest.Line(mouse, 2),
                new CreateOrderRequest.Line(keyboard, 1)));
        var differentQuantity = new CreateOrderRequest(List.of(
                new CreateOrderRequest.Line(keyboard, 2),
                new CreateOrderRequest.Line(mouse, 2)));

        assertThat(RequestFingerprint.hash(first))
                .hasSize(64)
                .isEqualTo(RequestFingerprint.hash(reversed))
                .isNotEqualTo(RequestFingerprint.hash(differentQuantity));
    }
}

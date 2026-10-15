package dev.naveencode.orderflow.order;

public record CreateOutcome(OrderResponse order, boolean replayed) {
}

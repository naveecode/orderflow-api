package dev.naveencode.orderflow.order;

import dev.naveencode.orderflow.common.Money;
import dev.naveencode.orderflow.error.ApiException;
import dev.naveencode.orderflow.error.ErrorCode;
import dev.naveencode.orderflow.product.Product;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrderLines {

    private OrderLines() {
    }

    public record PlannedLine(Product product, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public static List<PlannedLine> plan(List<CreateOrderRequest.Line> requested, Map<UUID, Product> products) {
        var seen = new HashSet<UUID>();
        var lines = new ArrayList<PlannedLine>();
        for (var line : requested) {
            if (!seen.add(line.productId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Duplicate product in order: " + line.productId());
            }
            var product = products.get(line.productId());
            if (product == null) {
                throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + line.productId());
            }
            if (!product.isActive()) {
                throw new ApiException(ErrorCode.PRODUCT_UNAVAILABLE, "Product is not available: " + product.getSku());
            }
            if (product.getStockQuantity() < line.quantity()) {
                throw new ApiException(
                        ErrorCode.INSUFFICIENT_STOCK,
                        "Not enough stock for " + product.getSku()
                                + ". Requested " + line.quantity()
                                + ", available " + product.getStockQuantity());
            }
            var unitPrice = Money.scale(product.getUnitPrice());
            var lineTotal = Money.scale(unitPrice.multiply(BigDecimal.valueOf(line.quantity())));
            lines.add(new PlannedLine(product, line.quantity(), unitPrice, lineTotal));
        }
        return List.copyOf(lines);
    }

    public static BigDecimal total(List<PlannedLine> lines) {
        return Money.scale(lines.stream().map(PlannedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}

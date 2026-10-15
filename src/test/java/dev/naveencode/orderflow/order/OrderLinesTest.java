package dev.naveencode.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.naveencode.orderflow.error.ApiException;
import dev.naveencode.orderflow.error.ErrorCode;
import dev.naveencode.orderflow.product.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderLinesTest {

    @Test
    void pricesLinesFromTheCatalogAndSumsThem() {
        var keyboard = product("OF-KBD-01", "129.00", 10);
        var mouse = product("OF-MSE-01", "49.00", 10);
        var requested = List.of(
                new CreateOrderRequest.Line(keyboard.getId(), 2),
                new CreateOrderRequest.Line(mouse.getId(), 1));

        var lines = OrderLines.plan(requested, Map.of(keyboard.getId(), keyboard, mouse.getId(), mouse));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).lineTotal()).isEqualByComparingTo("258.00");
        assertThat(lines.get(1).lineTotal()).isEqualByComparingTo("49.00");
        assertThat(OrderLines.total(lines)).isEqualByComparingTo("307.00");
    }

    @Test
    void rejectsADuplicateProduct() {
        var keyboard = product("OF-KBD-01", "129.00", 10);
        var requested = List.of(
                new CreateOrderRequest.Line(keyboard.getId(), 1),
                new CreateOrderRequest.Line(keyboard.getId(), 1));

        assertThatThrownBy(() -> OrderLines.plan(requested, Map.of(keyboard.getId(), keyboard)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsAnUnknownProduct() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> OrderLines.plan(
                List.of(new CreateOrderRequest.Line(missing, 1)),
                Map.of()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void rejectsAnInactiveProductBeforeCheckingStock() {
        var product = product("OF-OLD-01", "10.00", 0);
        product.update("Old", null, new BigDecimal("10.00"), 0, false);

        assertThatThrownBy(() -> OrderLines.plan(
                List.of(new CreateOrderRequest.Line(product.getId(), 1)),
                Map.of(product.getId(), product)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.PRODUCT_UNAVAILABLE);
    }

    @Test
    void rejectsInsufficientStock() {
        var product = product("OF-KBD-01", "129.00", 1);

        assertThatThrownBy(() -> OrderLines.plan(
                List.of(new CreateOrderRequest.Line(product.getId(), 2)),
                Map.of(product.getId(), product)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
    }

    private static Product product(String sku, String price, int stock) {
        var product = new Product(sku, sku, null, new BigDecimal(price), stock);
        setId(product, UUID.randomUUID());
        return product;
    }

    private static void setId(Product product, UUID id) {
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

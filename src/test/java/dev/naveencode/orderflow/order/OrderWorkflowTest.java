package dev.naveencode.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.naveencode.orderflow.user.Role;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OrderWorkflowTest {

    @ParameterizedTest
    @MethodSource("legalMoves")
    void allowsTheStatusGraph(OrderStatus from, OrderStatus to) {
        assertThat(OrderWorkflow.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("illegalMoves")
    void rejectsEverythingOutsideTheGraph(OrderStatus from, OrderStatus to) {
        assertThat(OrderWorkflow.canTransition(from, to)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("roleRules")
    void limitsCustomerMovesToPayAndCancel(Role role, OrderStatus target, boolean allowed) {
        assertThat(OrderWorkflow.permittedFor(role, target)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @MethodSource("stockRules")
    void restoresStockOnlyWhenCancellingBeforeShipment(OrderStatus from, OrderStatus to, boolean releases) {
        assertThat(OrderWorkflow.releasesStock(from, to)).isEqualTo(releases);
    }

    private static Stream<Arguments> legalMoves() {
        return Stream.of(
                Arguments.of(OrderStatus.CREATED, OrderStatus.PAID),
                Arguments.of(OrderStatus.CREATED, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.PAID, OrderStatus.SHIPPED),
                Arguments.of(OrderStatus.PAID, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    private static Stream<Arguments> illegalMoves() {
        return Stream.of(
                Arguments.of(OrderStatus.CREATED, OrderStatus.SHIPPED),
                Arguments.of(OrderStatus.CREATED, OrderStatus.DELIVERED),
                Arguments.of(OrderStatus.CREATED, OrderStatus.CREATED),
                Arguments.of(OrderStatus.PAID, OrderStatus.DELIVERED),
                Arguments.of(OrderStatus.PAID, OrderStatus.CREATED),
                Arguments.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.SHIPPED, OrderStatus.PAID),
                Arguments.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.DELIVERED, OrderStatus.SHIPPED),
                Arguments.of(OrderStatus.CANCELLED, OrderStatus.PAID),
                Arguments.of(OrderStatus.CANCELLED, OrderStatus.CREATED));
    }

    private static Stream<Arguments> roleRules() {
        return Stream.of(
                Arguments.of(Role.USER, OrderStatus.PAID, true),
                Arguments.of(Role.USER, OrderStatus.CANCELLED, true),
                Arguments.of(Role.USER, OrderStatus.SHIPPED, false),
                Arguments.of(Role.USER, OrderStatus.DELIVERED, false),
                Arguments.of(Role.ADMIN, OrderStatus.SHIPPED, true),
                Arguments.of(Role.ADMIN, OrderStatus.DELIVERED, true),
                Arguments.of(Role.ADMIN, OrderStatus.CANCELLED, true));
    }

    private static Stream<Arguments> stockRules() {
        return Stream.of(
                Arguments.of(OrderStatus.CREATED, OrderStatus.CANCELLED, true),
                Arguments.of(OrderStatus.PAID, OrderStatus.CANCELLED, true),
                Arguments.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED, false),
                Arguments.of(OrderStatus.CREATED, OrderStatus.PAID, false));
    }
}

package dev.naveencode.orderflow.order;

import dev.naveencode.orderflow.user.Role;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class OrderWorkflow {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.CREATED, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

    private OrderWorkflow() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static Set<OrderStatus> targets(OrderStatus from) {
        return Set.copyOf(ALLOWED.get(from));
    }

    public static boolean permittedFor(Role role, OrderStatus target) {
        if (role == Role.ADMIN) {
            return true;
        }
        return target == OrderStatus.PAID || target == OrderStatus.CANCELLED;
    }

    public static boolean releasesStock(OrderStatus from, OrderStatus to) {
        return to == OrderStatus.CANCELLED && (from == OrderStatus.CREATED || from == OrderStatus.PAID);
    }
}

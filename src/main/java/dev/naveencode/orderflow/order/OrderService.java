package dev.naveencode.orderflow.order;

import dev.naveencode.orderflow.common.PageRequests;
import dev.naveencode.orderflow.common.PageResponse;
import dev.naveencode.orderflow.error.ApiException;
import dev.naveencode.orderflow.error.ErrorCode;
import dev.naveencode.orderflow.idempotency.IdempotencyRecord;
import dev.naveencode.orderflow.idempotency.IdempotencyRepository;
import dev.naveencode.orderflow.idempotency.RequestFingerprint;
import dev.naveencode.orderflow.product.ProductRepository;
import dev.naveencode.orderflow.security.AuthenticatedUser;
import dev.naveencode.orderflow.user.Role;
import dev.naveencode.orderflow.user.UserAccountRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

    private static final Set<String> SORTS = Set.of("createdAt", "status", "totalAmount");

    private final CustomerOrderRepository orders;
    private final IdempotencyRepository idempotency;
    private final ProductRepository products;
    private final UserAccountRepository users;
    private final TransactionTemplate transactions;

    public OrderService(
            CustomerOrderRepository orders,
            IdempotencyRepository idempotency,
            ProductRepository products,
            UserAccountRepository users,
            PlatformTransactionManager transactionManager) {
        this.orders = orders;
        this.idempotency = idempotency;
        this.products = products;
        this.users = users;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public CreateOutcome create(AuthenticatedUser actor, String idempotencyKey, CreateOrderRequest request) {
        String hash = RequestFingerprint.hash(request);
        var existing = idempotency.findByUserIdAndIdempotencyKey(actor.id(), idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                var order = place(actor, idempotencyKey, hash, request);
                return new CreateOutcome(OrderResponse.from(order), false);
            }));
        } catch (RuntimeException exception) {
            if (!isConstraintViolation(exception) && !isOptimisticLock(exception)) {
                throw exception;
            }
            var winner = idempotency.findByUserIdAndIdempotencyKey(actor.id(), idempotencyKey);
            if (winner.isEmpty()) {
                throw exception;
            }
            return replay(winner.get(), hash);
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse get(AuthenticatedUser actor, UUID orderId) {
        return OrderResponse.from(visibleOrder(actor, orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(
            AuthenticatedUser actor,
            OrderStatus status,
            Instant createdFrom,
            Instant createdTo,
            UUID userId,
            int page,
            int size,
            String sort) {
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "createdFrom must be before createdTo");
        }
        UUID owner = actor.role() == Role.ADMIN ? userId : actor.id();
        Specification<CustomerOrder> specification = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (owner != null) {
                predicates.add(cb.equal(root.get("user").get("id"), owner));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), createdTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        var result = orders.findAll(specification, PageRequests.of(page, size, sort, SORTS, "createdAt"));
        return PageResponse.from(result.map(OrderResponse::from));
    }

    public OrderResponse transition(AuthenticatedUser actor, UUID orderId, OrderStatus target) {
        return Objects.requireNonNull(transactions.execute(status -> {
            var order = visibleOrder(actor, orderId);
            if (!OrderWorkflow.canTransition(order.getStatus(), target)) {
                throw new ApiException(
                        ErrorCode.INVALID_TRANSITION,
                        "Cannot transition order from " + order.getStatus() + " to " + target);
            }
            if (!OrderWorkflow.permittedFor(actor.role(), target)) {
                throw new ApiException(ErrorCode.FORBIDDEN, "You cannot move an order to " + target);
            }
            if (OrderWorkflow.releasesStock(order.getStatus(), target)) {
                for (var item : order.getItems()) {
                    item.getProduct().incrementStock(item.getQuantity());
                }
            }
            order.applyStatus(target);
            return OrderResponse.from(order);
        }));
    }

    private CustomerOrder place(
            AuthenticatedUser actor,
            String idempotencyKey,
            String hash,
            CreateOrderRequest request) {
        var user = users.findById(actor.id())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Unknown account"));
        var ids = request.items().stream()
                .map(CreateOrderRequest.Line::productId)
                .distinct()
                .sorted()
                .toList();
        var byId = products.findAllById(ids).stream()
                .collect(Collectors.toMap(product -> product.getId(), product -> product));
        var planned = OrderLines.plan(request.items(), byId);
        var order = new CustomerOrder(user);
        for (var line : planned) {
            line.product().decrementStock(line.quantity());
            order.addItem(line.product(), line.quantity(), line.unitPrice());
        }
        orders.saveAndFlush(order);
        idempotency.saveAndFlush(new IdempotencyRecord(user, idempotencyKey, hash, order));
        return order;
    }

    private CustomerOrder visibleOrder(AuthenticatedUser actor, UUID orderId) {
        var order = orders.findWithDetailsById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found"));
        if (actor.role() != Role.ADMIN && !order.getUser().getId().equals(actor.id())) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order not found");
        }
        return order;
    }

    private static CreateOutcome replay(IdempotencyRecord record, String hash) {
        if (!record.getRequestHash().equals(hash)) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "This Idempotency-Key was already used with a different request body");
        }
        return new CreateOutcome(OrderResponse.from(record.getOrder()), true);
    }

    private static boolean isConstraintViolation(Throwable exception) {
        return hasCause(exception, DataIntegrityViolationException.class, ConstraintViolationException.class);
    }

    private static boolean isOptimisticLock(Throwable exception) {
        return hasCause(exception, ObjectOptimisticLockingFailureException.class, OptimisticLockException.class);
    }

    @SafeVarargs
    private static boolean hasCause(Throwable exception, Class<? extends Throwable>... types) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            for (Class<? extends Throwable> type : types) {
                if (type.isInstance(current)) {
                    return true;
                }
            }
        }
        return false;
    }
}

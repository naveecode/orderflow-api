package dev.naveencode.orderflow.order;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID>, JpaSpecificationExecutor<CustomerOrder> {

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<CustomerOrder> findWithDetailsById(UUID id);
}

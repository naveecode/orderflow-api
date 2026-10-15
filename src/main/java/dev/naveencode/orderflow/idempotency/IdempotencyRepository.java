package dev.naveencode.orderflow.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    @EntityGraph(attributePaths = {"order", "order.items", "order.items.product", "order.user"})
    @Query("""
            select record from IdempotencyRecord record
            where record.user.id = :userId and record.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey);
}

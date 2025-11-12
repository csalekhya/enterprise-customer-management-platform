package io.crmplatform.repository;

import io.crmplatform.domain.Interaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    /** Activity mix for the dashboard: one row per interaction type over a window. */
    interface TypeCountRow {
        Interaction.Type getType();

        long getTotal();
    }

    @Override
    @EntityGraph(attributePaths = {"customer", "createdByUser"})
    Optional<Interaction> findById(Long id);

    /** Timeline for one contact, newest first - served by idx_interactions_customer_occurred. */
    @EntityGraph(attributePaths = {"customer", "createdByUser"})
    Page<Interaction> findByCustomerIdOrderByOccurredAtDesc(Long customerId, Pageable pageable);

    /** Global activity feed, optionally narrowed to one account. */
    @EntityGraph(attributePaths = {"customer", "createdByUser"})
    Page<Interaction> findByCustomerAccountIdOrderByOccurredAtDesc(Long accountId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "createdByUser"})
    Page<Interaction> findAllByOrderByOccurredAtDesc(Pageable pageable);

    long countByOccurredAtGreaterThanEqual(Instant since);

    @Query(
            """
            select i.type as type, count(i) as total
            from Interaction i
            where i.occurredAt >= :since
            group by i.type
            """)
    List<TypeCountRow> countByTypeSince(@Param("since") Instant since);
}

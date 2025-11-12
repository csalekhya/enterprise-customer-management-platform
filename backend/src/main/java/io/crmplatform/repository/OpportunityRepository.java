package io.crmplatform.repository;

import io.crmplatform.domain.Opportunity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long>, JpaSpecificationExecutor<Opportunity> {

    /**
     * Pipeline value per stage.
     *
     * <p>This is the whole reason the dashboard is fast: one {@code GROUP BY} returns every bar of
     * the pipeline chart. The naive alternative - iterate the five stages and issue a
     * {@code SUM(amount)} per stage, or worse, load all open deals and fold them in Java - is a
     * textbook N+1 that grows with the stage enum and moves the entire deal table over the wire.
     */
    interface StageAggregateRow {
        Opportunity.Stage getStage();

        BigDecimal getTotalAmount();

        BigDecimal getWeightedAmount();

        long getDealCount();
    }

    @Override
    @EntityGraph(attributePaths = {"account", "ownerUser"})
    Optional<Opportunity> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"account", "ownerUser"})
    Page<Opportunity> findAll(Specification<Opportunity> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"account", "ownerUser"})
    List<Opportunity> findByStageOrderByExpectedCloseDateAsc(Opportunity.Stage stage);

    long countByAccountId(Long accountId);

    @Query(
            """
            select o.stage as stage,
                   coalesce(sum(o.amount), 0) as totalAmount,
                   coalesce(sum(o.amount * o.probability / 100), 0) as weightedAmount,
                   count(o) as dealCount
            from Opportunity o
            group by o.stage
            """)
    List<StageAggregateRow> aggregateByStage();
}

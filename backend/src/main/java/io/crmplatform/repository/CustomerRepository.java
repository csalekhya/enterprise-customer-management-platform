package io.crmplatform.repository;

import io.crmplatform.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    /** Funnel breakdown for the dashboard: one row per lifecycle stage. */
    interface StageCountRow {
        Customer.LifecycleStage getStage();

        long getContactCount();
    }

    @Override
    @EntityGraph(attributePaths = {"account", "ownerUser"})
    Optional<Customer> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"account", "ownerUser"})
    Page<Customer> findAll(Specification<Customer> specification, Pageable pageable);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    long countByAccountId(Long accountId);

    /** Uses idx_customers_created_at as a plain range scan. */
    long countByCreatedAtGreaterThanEqual(Instant since);

    @Query(
            """
            select c.lifecycleStage as stage, count(c) as contactCount
            from Customer c
            group by c.lifecycleStage
            """)
    List<StageCountRow> countByLifecycleStage();

    /**
     * Ownership probe used by the service layer before a mutation.
     *
     * <p>Deliberately not {@code findById(...).getOwnerUser()}: this returns a single boolean from
     * the covering index {@code idx_customers_owner_stage} instead of hydrating an entity graph
     * only to read one foreign key off it.
     */
    @Query("select (count(c) > 0) from Customer c where c.id = :id and c.ownerUser.id = :userId")
    boolean isOwnedBy(@Param("id") Long id, @Param("userId") Long userId);
}

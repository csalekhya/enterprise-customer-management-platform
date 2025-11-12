package io.crmplatform.repository;

import io.crmplatform.domain.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    /**
     * Row shape for the dashboard's "top accounts by revenue" widget. An interface projection keeps
     * the query narrow - four scalar columns instead of hydrating whole {@code Account} entities
     * that would then be discarded.
     */
    interface TopAccountRow {
        Long getId();

        String getName();

        Account.Tier getTier();

        BigDecimal getAnnualRevenue();
    }

    @Override
    @EntityGraph(attributePaths = "ownerUser")
    Optional<Account> findById(Long id);

    /**
     * Specification + entity graph together: the graph turns the owner association into a join on
     * the same statement, so rendering a 25-row page costs one query rather than 26.
     */
    @Override
    @EntityGraph(attributePaths = "ownerUser")
    Page<Account> findAll(Specification<Account> specification, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    long countByStatus(Account.Status status);

    @Query(
            """
            select a.id as id, a.name as name, a.tier as tier, a.annualRevenue as annualRevenue
            from Account a
            where a.annualRevenue is not null and a.status <> :excludedStatus
            order by a.annualRevenue desc
            """)
    List<TopAccountRow> findTopByRevenue(@Param("excludedStatus") Account.Status excludedStatus, Pageable pageable);
}

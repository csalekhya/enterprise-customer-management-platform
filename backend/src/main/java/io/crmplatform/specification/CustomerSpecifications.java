package io.crmplatform.specification;

import io.crmplatform.domain.Customer;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Composable filters for the customer list endpoint.
 *
 * <p>Every method returns {@code null} for an absent value, and {@link #build} folds the non-null
 * ones together with {@code AND}. That is the whole trick behind an endpoint that accepts any
 * subset of six query parameters without a combinatorial explosion of repository methods or a
 * hand-concatenated JPQL string - and because the Criteria API binds every literal as a JDBC
 * parameter, the dynamic filter cannot become an injection vector.
 */
public final class CustomerSpecifications {

    private CustomerSpecifications() {}

    /** Case-insensitive fragment match across name and email. */
    public static Specification<Customer> matchesText(String fragment) {
        if (!StringUtils.hasText(fragment)) {
            return null;
        }
        String needle = "%" + fragment.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            List<Predicate> alternatives = List.of(
                    cb.like(cb.lower(root.get("firstName")), needle),
                    cb.like(cb.lower(root.get("lastName")), needle),
                    cb.like(cb.lower(root.get("email")), needle),
                    cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), needle));
            return cb.or(alternatives.toArray(Predicate[]::new));
        };
    }

    public static Specification<Customer> hasLifecycleStage(Customer.LifecycleStage stage) {
        return stage == null ? null : (root, query, cb) -> cb.equal(root.get("lifecycleStage"), stage);
    }

    public static Specification<Customer> belongsToAccount(Long accountId) {
        return accountId == null ? null : (root, query, cb) -> cb.equal(root.get("account").get("id"), accountId);
    }

    public static Specification<Customer> ownedBy(Long ownerUserId) {
        return ownerUserId == null ? null : (root, query, cb) -> cb.equal(root.get("ownerUser").get("id"), ownerUserId);
    }

    /** Substring match against the denormalised tag list. */
    public static Specification<Customer> hasTag(String tag) {
        if (!StringUtils.hasText(tag)) {
            return null;
        }
        String needle = "%" + tag.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("tags")), needle);
    }

    /** Restricts results to one account tier by walking the account association. */
    public static Specification<Customer> accountTier(io.crmplatform.domain.Account.Tier tier) {
        return tier == null ? null : (root, query, cb) -> cb.equal(root.get("account").get("tier"), tier);
    }

    /**
     * Combines the supplied filters, ignoring the absent ones.
     *
     * @return an always-true specification when nothing was supplied, so callers never branch
     */
    public static Specification<Customer> build(
            String search,
            Customer.LifecycleStage stage,
            Long accountId,
            Long ownerUserId,
            String tag,
            io.crmplatform.domain.Account.Tier tier) {

        List<Specification<Customer>> parts = new ArrayList<>();
        parts.add(matchesText(search));
        parts.add(hasLifecycleStage(stage));
        parts.add(belongsToAccount(accountId));
        parts.add(ownedBy(ownerUserId));
        parts.add(hasTag(tag));
        parts.add(accountTier(tier));

        Specification<Customer> combined = (root, query, cb) -> cb.conjunction();
        for (Specification<Customer> part : parts) {
            if (part != null) {
                combined = combined.and(part);
            }
        }
        return combined;
    }
}

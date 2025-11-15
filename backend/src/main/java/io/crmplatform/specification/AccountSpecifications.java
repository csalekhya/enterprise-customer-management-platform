package io.crmplatform.specification;

import io.crmplatform.domain.Account;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Composable filters for the account list endpoint. Same contract as {@link CustomerSpecifications}. */
public final class AccountSpecifications {

    private AccountSpecifications() {}

    public static Specification<Account> matchesText(String fragment) {
        if (!StringUtils.hasText(fragment)) {
            return null;
        }
        String needle = "%" + fragment.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            List<Predicate> alternatives =
                    List.of(cb.like(cb.lower(root.get("name")), needle), cb.like(cb.lower(root.get("website")), needle));
            return cb.or(alternatives.toArray(Predicate[]::new));
        };
    }

    public static Specification<Account> hasTier(Account.Tier tier) {
        return tier == null ? null : (root, query, cb) -> cb.equal(root.get("tier"), tier);
    }

    public static Specification<Account> hasStatus(Account.Status status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Account> hasIndustry(Account.Industry industry) {
        return industry == null ? null : (root, query, cb) -> cb.equal(root.get("industry"), industry);
    }

    public static Specification<Account> ownedBy(Long ownerUserId) {
        return ownerUserId == null ? null : (root, query, cb) -> cb.equal(root.get("ownerUser").get("id"), ownerUserId);
    }

    /** Segment filter for the revenue report. */
    public static Specification<Account> revenueAtLeast(BigDecimal floor) {
        return floor == null
                ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("annualRevenue"), floor);
    }

    public static Specification<Account> build(
            String search,
            Account.Tier tier,
            Account.Status status,
            Account.Industry industry,
            Long ownerUserId,
            BigDecimal minRevenue) {

        List<Specification<Account>> parts = new ArrayList<>();
        parts.add(matchesText(search));
        parts.add(hasTier(tier));
        parts.add(hasStatus(status));
        parts.add(hasIndustry(industry));
        parts.add(ownedBy(ownerUserId));
        parts.add(revenueAtLeast(minRevenue));

        Specification<Account> combined = (root, query, cb) -> cb.conjunction();
        for (Specification<Account> part : parts) {
            if (part != null) {
                combined = combined.and(part);
            }
        }
        return combined;
    }
}

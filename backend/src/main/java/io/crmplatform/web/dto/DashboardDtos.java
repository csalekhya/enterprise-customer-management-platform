package io.crmplatform.web.dto;

import io.crmplatform.domain.Account;
import io.crmplatform.domain.Customer;
import io.crmplatform.domain.Interaction;
import io.crmplatform.domain.Opportunity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Dashboard read model.
 *
 * <p>{@link Summary} is {@code Serializable} because it is the value stored in the
 * {@code dashboardSummary} cache; keeping it serialisable means swapping Caffeine for a
 * distributed cache later needs no DTO changes.
 */
public final class DashboardDtos {

    private DashboardDtos() {}

    public record Summary(
            long totalAccounts,
            long activeAccounts,
            long prospectAccounts,
            long totalContacts,
            long newContactsThisMonth,
            long interactionsLast30Days,
            BigDecimal openPipelineValue,
            BigDecimal weightedPipelineValue,
            BigDecimal wonValueAllTime,
            List<StageValue> pipelineByStage,
            List<FunnelBucket> contactFunnel,
            List<ActivityBucket> activityMix,
            List<TopAccount> topAccountsByRevenue,
            Instant generatedAt)
            implements Serializable {}

    public record StageValue(Opportunity.Stage stage, BigDecimal amount, BigDecimal weightedAmount, long dealCount)
            implements Serializable {}

    public record FunnelBucket(Customer.LifecycleStage stage, long contactCount) implements Serializable {}

    public record ActivityBucket(Interaction.Type type, long total) implements Serializable {}

    public record TopAccount(Long id, String name, Account.Tier tier, BigDecimal annualRevenue)
            implements Serializable {}
}

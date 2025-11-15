package io.crmplatform.service;

import io.crmplatform.config.CacheConfig;
import io.crmplatform.domain.Account;
import io.crmplatform.domain.Opportunity;
import io.crmplatform.repository.AccountRepository;
import io.crmplatform.repository.CustomerRepository;
import io.crmplatform.repository.InteractionRepository;
import io.crmplatform.repository.OpportunityRepository;
import io.crmplatform.web.dto.DashboardDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only analytics for the dashboard.
 *
 * <p><b>Why aggregates and not entities.</b> Every number on this screen is a fold over a table.
 * The obvious implementation - load the opportunities, group them in a stream, then count contacts
 * per stage the same way - reads the entire pipeline into the heap on every page load and, once
 * lazy associations are touched, degenerates into one query per row: the N+1 that makes CRM
 * dashboards notorious. Instead each widget is served by a single {@code GROUP BY} or
 * {@code COUNT} executed in the database, which returns tens of bytes per widget regardless of how
 * many deals exist. The whole screen is 9 statements, constant in the size of the dataset.
 *
 * <p><b>Why cached.</b> Even at 9 statements this is the most-requested endpoint in the product -
 * every user opens it first, and several refresh it all day. A 60 second Caffeine window collapses
 * the morning login stampede into one database round trip per minute per instance while staying
 * well inside what a human perceives as live.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int TOP_ACCOUNT_LIMIT = 5;
    private static final int ACTIVITY_WINDOW_DAYS = 30;

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final InteractionRepository interactionRepository;
    private final OpportunityRepository opportunityRepository;
    private final Clock clock = Clock.systemUTC();

    @Cacheable(cacheNames = CacheConfig.DASHBOARD_SUMMARY, key = "'global'")
    public DashboardDtos.Summary summary() {
        Instant now = clock.instant();
        Instant activitySince = now.minusSeconds(ACTIVITY_WINDOW_DAYS * 86_400L);
        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        // 1 query: pipeline value, weighted value and deal count for all five stages at once.
        List<OpportunityRepository.StageAggregateRow> stageRows = opportunityRepository.aggregateByStage();
        Map<Opportunity.Stage, DashboardDtos.StageValue> byStage = new EnumMap<>(Opportunity.Stage.class);
        for (OpportunityRepository.StageAggregateRow row : stageRows) {
            byStage.put(
                    row.getStage(),
                    new DashboardDtos.StageValue(
                            row.getStage(), row.getTotalAmount(), row.getWeightedAmount(), row.getDealCount()));
        }

        // Stages with no deals are absent from a GROUP BY result; the chart still needs their bars.
        List<DashboardDtos.StageValue> pipelineByStage = new ArrayList<>();
        for (Opportunity.Stage stage : Opportunity.Stage.values()) {
            pipelineByStage.add(byStage.getOrDefault(
                    stage, new DashboardDtos.StageValue(stage, BigDecimal.ZERO, BigDecimal.ZERO, 0L)));
        }

        BigDecimal openPipeline = pipelineByStage.stream()
                .filter(value -> value.stage().isOpen())
                .map(DashboardDtos.StageValue::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal weightedPipeline = pipelineByStage.stream()
                .filter(value -> value.stage().isOpen())
                .map(DashboardDtos.StageValue::weightedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal wonValue = byStage.containsKey(Opportunity.Stage.WON)
                ? byStage.get(Opportunity.Stage.WON).amount()
                : BigDecimal.ZERO;

        // 1 query: contact counts per lifecycle stage for the funnel.
        List<DashboardDtos.FunnelBucket> funnel = customerRepository.countByLifecycleStage().stream()
                .map(row -> new DashboardDtos.FunnelBucket(row.getStage(), row.getContactCount()))
                .toList();
        long totalContacts = funnel.stream().mapToLong(DashboardDtos.FunnelBucket::contactCount).sum();

        // 1 query: activity mix over the trailing window.
        List<DashboardDtos.ActivityBucket> activityMix = interactionRepository.countByTypeSince(activitySince).stream()
                .map(row -> new DashboardDtos.ActivityBucket(row.getType(), row.getTotal()))
                .toList();

        // 1 query: top accounts, limited in SQL rather than trimmed in Java.
        List<DashboardDtos.TopAccount> topAccounts = accountRepository
                .findTopByRevenue(Account.Status.CHURNED, PageRequest.of(0, TOP_ACCOUNT_LIMIT))
                .stream()
                .map(row -> new DashboardDtos.TopAccount(
                        row.getId(), row.getName(), row.getTier(), row.getAnnualRevenue()))
                .toList();

        // 5 cheap counts: three on accounts, then contacts this month and activity in the window.
        long totalAccounts = accountRepository.count();
        long activeAccounts = accountRepository.countByStatus(Account.Status.ACTIVE);
        long prospectAccounts = accountRepository.countByStatus(Account.Status.PROSPECT);

        return new DashboardDtos.Summary(
                totalAccounts,
                activeAccounts,
                prospectAccounts,
                totalContacts,
                customerRepository.countByCreatedAtGreaterThanEqual(startOfMonth),
                interactionRepository.countByOccurredAtGreaterThanEqual(activitySince),
                openPipeline,
                weightedPipeline,
                wonValue,
                pipelineByStage,
                funnel,
                activityMix,
                topAccounts,
                now);
    }

    /**
     * Drops the cached summary.
     *
     * <p>Exposed so that a write which visibly moves a KPI - closing a deal, for instance - can
     * make the dashboard truthful immediately instead of waiting out the TTL.
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD_SUMMARY, allEntries = true)
    public void invalidate() {
        // Annotation-driven; nothing to do in the body.
    }
}

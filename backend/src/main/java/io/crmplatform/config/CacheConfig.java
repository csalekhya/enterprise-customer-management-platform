package io.crmplatform.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * In-process caching with Caffeine.
 *
 * <p>Two caches with deliberately different lifetimes:
 *
 * <ul>
 *   <li>{@link #DASHBOARD_SUMMARY} - 60 s. The dashboard fans out to several aggregate queries and
 *       is the first thing every user loads each morning, so it is both the most expensive and the
 *       most repeated read. A one-minute window is invisible to a human reading KPIs but collapses
 *       a login stampede into a single database hit.
 *   <li>{@link #REFERENCE_DATA} - 30 min. Roles and enum option lists change only on deploy.
 * </ul>
 *
 * <p>Caffeine (in-process) rather than Redis is the right call at this size: the cached payloads
 * are small, tolerate per-instance divergence for their TTL, and an extra network hop would cost
 * more than the query it saves. The scaling path to a shared cache is a one-bean change and is
 * documented in {@code docs/ARCHITECTURE.md}.
 */
@Configuration
public class CacheConfig {

    public static final String DASHBOARD_SUMMARY = "dashboardSummary";
    public static final String REFERENCE_DATA = "referenceData";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(DASHBOARD_SUMMARY, REFERENCE_DATA));
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(500)
                .recordStats());

        manager.registerCustomCache(
                REFERENCE_DATA,
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(30))
                        .maximumSize(100)
                        .recordStats()
                        .build());

        return manager;
    }
}

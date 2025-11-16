package io.crmplatform.web;

import io.crmplatform.service.DashboardService;
import io.crmplatform.web.dto.DashboardDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregate KPIs for the landing screen")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * KPI bundle for the dashboard.
     *
     * <p>A private, 30 second {@code Cache-Control} mirrors the server-side TTL, so a browser that
     * re-mounts the page during a navigation does not even reach the API. Marked {@code private}
     * because the payload describes one tenant's commercial position and must never sit in a shared
     * proxy cache.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Accounts, pipeline by stage, funnel, activity mix and top accounts")
    public ResponseEntity<DashboardDtos.Summary> summary() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePrivate())
                .body(dashboardService.summary());
    }
}

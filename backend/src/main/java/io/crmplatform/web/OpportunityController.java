package io.crmplatform.web;

import io.crmplatform.domain.Opportunity;
import io.crmplatform.service.DashboardService;
import io.crmplatform.service.OpportunityService;
import io.crmplatform.web.dto.OpportunityDtos;
import io.crmplatform.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/opportunities")
@RequiredArgsConstructor
@Tag(name = "Opportunities", description = "Sales pipeline: deals, stages and the kanban board")
public class OpportunityController {

    private final OpportunityService opportunityService;
    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Paged deal list")
    public PageResponse<OpportunityDtos.Response> list(
            @RequestParam(required = false) Opportunity.Stage stage,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long ownerUserId,
            @PageableDefault(size = 20, sort = "expectedCloseDate", direction = Sort.Direction.ASC)
                    Pageable pageable) {

        return opportunityService.list(stage, accountId, ownerUserId, pageable);
    }

    @GetMapping("/board")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Deals grouped by stage, ready to render as kanban columns")
    public Map<Opportunity.Stage, List<OpportunityDtos.Response>> board() {
        return opportunityService.board();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Fetch one deal")
    public OpportunityDtos.Response get(@PathVariable Long id) {
        return opportunityService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Create a deal")
    public ResponseEntity<OpportunityDtos.Response> create(
            @Valid @RequestBody OpportunityDtos.UpsertRequest request) {
        OpportunityDtos.Response created = opportunityService.create(request);
        dashboardService.invalidate();
        return ResponseEntity.created(URI.create("/api/v1/opportunities/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Replace a deal. Reps may only update deals they own")
    public OpportunityDtos.Response update(
            @PathVariable Long id, @Valid @RequestBody OpportunityDtos.UpsertRequest request) {
        OpportunityDtos.Response updated = opportunityService.update(id, request);
        dashboardService.invalidate();
        return updated;
    }

    /**
     * Stage transition from the board. Explicitly invalidates the dashboard cache, because moving a
     * deal is exactly the kind of write a user expects to see reflected in the KPIs immediately.
     */
    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Move a deal to another stage")
    public OpportunityDtos.Response changeStage(
            @PathVariable Long id, @Valid @RequestBody OpportunityDtos.StageChangeRequest request) {
        OpportunityDtos.Response moved = opportunityService.changeStage(id, request);
        dashboardService.invalidate();
        return moved;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    @Operation(summary = "Delete a deal")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        opportunityService.delete(id);
        dashboardService.invalidate();
        return ResponseEntity.noContent().build();
    }
}

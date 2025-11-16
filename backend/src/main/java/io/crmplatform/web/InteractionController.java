package io.crmplatform.web;

import io.crmplatform.service.InteractionService;
import io.crmplatform.web.dto.InteractionDtos;
import io.crmplatform.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/interactions")
@RequiredArgsConstructor
@Tag(name = "Interactions", description = "Append-only activity trail: calls, emails, meetings and notes")
public class InteractionController {

    private final InteractionService interactionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Newest-first timeline, optionally scoped to a contact or an account")
    public PageResponse<InteractionDtos.Response> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long accountId,
            @PageableDefault(size = 20) Pageable pageable) {

        return interactionService.list(customerId, accountId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Fetch one activity entry")
    public InteractionDtos.Response get(@PathVariable Long id) {
        return interactionService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Log an activity against a contact. The caller is recorded as the author")
    public ResponseEntity<InteractionDtos.Response> create(
            @Valid @RequestBody InteractionDtos.CreateRequest request) {
        InteractionDtos.Response created = interactionService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/interactions/" + created.id())).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Remove an entry. Restricted to its author, a manager or an administrator")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        interactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

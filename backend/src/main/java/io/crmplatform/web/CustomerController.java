package io.crmplatform.web;

import io.crmplatform.domain.Account;
import io.crmplatform.domain.Customer;
import io.crmplatform.service.CustomerService;
import io.crmplatform.web.dto.CustomerDtos;
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

/**
 * Contact endpoints.
 *
 * <p>Role requirements sit on the handlers so that the rule and the route are read together.
 * Record-level ownership is enforced one layer down, in {@link CustomerService}: a SALES_REP passes
 * the annotation here and is still refused a contact they do not own.
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Contacts inside an account, with server-side paging and filtering")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Paged, sorted and filtered contact list")
    public PageResponse<CustomerDtos.Response> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Customer.LifecycleStage stage,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Account.Tier tier,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {

        return customerService.list(search, stage, accountId, ownerUserId, tag, tier, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Fetch one contact")
    public CustomerDtos.Response get(@PathVariable Long id) {
        return customerService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Create a contact. Reps become the owner unless a manager assigns another")
    public ResponseEntity<CustomerDtos.Response> create(@Valid @RequestBody CustomerDtos.UpsertRequest request) {
        CustomerDtos.Response created = customerService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Replace a contact. Reps may only update contacts they own")
    public CustomerDtos.Response update(
            @PathVariable Long id, @Valid @RequestBody CustomerDtos.UpsertRequest request) {
        return customerService.update(id, request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Partial update for inline edits such as a lifecycle stage change")
    public CustomerDtos.Response patch(@PathVariable Long id, @Valid @RequestBody CustomerDtos.PatchRequest request) {
        return customerService.patch(id, request);
    }

    @PutMapping("/{id}/owner")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    @Operation(summary = "Transfer ownership of a contact")
    public CustomerDtos.Response reassign(@PathVariable Long id, @RequestParam Long ownerUserId) {
        return customerService.reassign(id, ownerUserId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Delete a contact and its activity trail")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

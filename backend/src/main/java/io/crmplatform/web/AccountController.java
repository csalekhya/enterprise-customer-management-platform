package io.crmplatform.web;

import io.crmplatform.domain.Account;
import io.crmplatform.service.AccountService;
import io.crmplatform.web.dto.AccountDtos;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Companies: the aggregate root of the CRM")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Paged, sorted and filtered account list")
    public PageResponse<AccountDtos.Response> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Account.Tier tier,
            @RequestParam(required = false) Account.Status status,
            @RequestParam(required = false) Account.Industry industry,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) BigDecimal minRevenue,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        return accountService.list(search, tier, status, industry, ownerUserId, minRevenue, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Fetch one account with its contact and pipeline counts")
    public AccountDtos.Response get(@PathVariable Long id) {
        return accountService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Create an account")
    public ResponseEntity<AccountDtos.Response> create(@Valid @RequestBody AccountDtos.UpsertRequest request) {
        AccountDtos.Response created = accountService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP')")
    @Operation(summary = "Replace an account. Reps may only update accounts they own")
    public AccountDtos.Response update(@PathVariable Long id, @Valid @RequestBody AccountDtos.UpsertRequest request) {
        return accountService.update(id, request);
    }

    @PutMapping("/{id}/owner")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    @Operation(summary = "Transfer ownership of an account")
    public AccountDtos.Response reassign(@PathVariable Long id, @RequestParam Long ownerUserId) {
        return accountService.reassign(id, ownerUserId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    @Operation(summary = "Delete an account. Refused while contacts or opportunities remain")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

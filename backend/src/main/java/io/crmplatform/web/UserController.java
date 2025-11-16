package io.crmplatform.web;

import io.crmplatform.service.UserService;
import io.crmplatform.web.dto.PageResponse;
import io.crmplatform.web.dto.UserDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User administration and owner lookup")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Paged user directory with roles")
    public PageResponse<UserDtos.Response> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.list(search, pageable);
    }

    /**
     * Owner picker options.
     *
     * <p>Readable by every role because the account, contact and deal forms all render an owner
     * dropdown; the payload is intentionally limited to id, display name and email.
     */
    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_REP', 'READ_ONLY')")
    @Operation(summary = "Enabled users as owner-picker options (cached reference data)")
    public List<UserDtos.Option> options() {
        return userService.options();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Fetch one user")
    public UserDtos.Response get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a user and assign roles")
    public ResponseEntity<UserDtos.Response> create(@Valid @RequestBody UserDtos.CreateRequest request) {
        UserDtos.Response created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a user's profile, roles or enabled flag; revokes their refresh tokens")
    public UserDtos.Response update(@PathVariable Long id, @Valid @RequestBody UserDtos.UpdateRequest request) {
        return userService.update(id, request);
    }
}

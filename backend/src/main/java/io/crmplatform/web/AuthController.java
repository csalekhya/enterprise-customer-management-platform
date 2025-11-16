package io.crmplatform.web;

import io.crmplatform.security.CrmUserDetails;
import io.crmplatform.service.AuthService;
import io.crmplatform.web.dto.AuthDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Password grant, refresh rotation and session teardown")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange credentials for an access + refresh token pair")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Rotate a refresh token for a new pair; the presented token is revoked")
    public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Revoke a refresh token. Idempotent, and safe to call with an expired access token")
    public ResponseEntity<Void> logout(@Valid @RequestBody AuthDtos.LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated user, their roles and last login timestamp")
    public AuthDtos.AuthenticatedUser me(@AuthenticationPrincipal CrmUserDetails principal) {
        return authService.currentUser(principal);
    }
}

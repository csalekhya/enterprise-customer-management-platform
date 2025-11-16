package io.crmplatform.web.dto;

import io.crmplatform.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/** Payloads for user administration (ADMIN only). */
public final class UserDtos {

    private UserDtos() {}

    public record CreateRequest(
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank
                    @Size(min = 10, max = 128)
                    @Pattern(
                            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                            message = "Password must contain an upper case letter, a lower case letter and a digit")
                    String password,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotEmpty(message = "Assign at least one role") Set<Role.Name> roles) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotNull Boolean enabled,
            @NotEmpty(message = "Assign at least one role") Set<Role.Name> roles) {}

    public record Response(
            Long id,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            Set<Role.Name> roles,
            Instant lastLoginAt,
            Instant createdAt) {}

    /** Owner picker option. */
    public record Option(Long id, String name, String email) {}
}

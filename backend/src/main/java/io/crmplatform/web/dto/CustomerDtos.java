package io.crmplatform.web.dto;

import io.crmplatform.domain.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Payloads for the customer (contact) resource. */
public final class CustomerDtos {

    private CustomerDtos() {}

    public record UpsertRequest(
            @NotNull Long accountId,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Email @Size(max = 180) String email,
            @Size(max = 32)
                    @Pattern(
                            regexp = "^$|^[+0-9][0-9 ()./-]{5,31}$",
                            message = "Phone may contain digits, spaces and + ( ) . / - only")
                    String phone,
            @Size(max = 120) String jobTitle,
            @NotNull Customer.LifecycleStage lifecycleStage,
            Long ownerUserId,
            @Size(max = 255) String tags) {}

    /** Partial update used by inline edits on the data table (stage change, re-tagging). */
    public record PatchRequest(Customer.LifecycleStage lifecycleStage, @Size(max = 255) String tags) {}

    public record Response(
            Long id,
            Long accountId,
            String accountName,
            String firstName,
            String lastName,
            String email,
            String phone,
            String jobTitle,
            Customer.LifecycleStage lifecycleStage,
            Long ownerUserId,
            String ownerName,
            String tags,
            Instant createdAt,
            Instant updatedAt) {}
}

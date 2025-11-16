package io.crmplatform.web.dto;

import io.crmplatform.domain.Interaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Payloads for the interaction (activity) resource. */
public final class InteractionDtos {

    private InteractionDtos() {}

    public record CreateRequest(
            @NotNull Long customerId,
            @NotNull Interaction.Type type,
            @NotBlank @Size(max = 180) String subject,
            @Size(max = 4000) String body,
            @NotNull @PastOrPresent(message = "An interaction cannot be logged in the future")
                    Instant occurredAt) {}

    public record Response(
            Long id,
            Long customerId,
            String customerName,
            Long accountId,
            String accountName,
            Interaction.Type type,
            String subject,
            String body,
            Instant occurredAt,
            Long authorId,
            String authorName,
            Instant createdAt) {}
}

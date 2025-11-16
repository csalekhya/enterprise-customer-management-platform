package io.crmplatform.web.dto;

import io.crmplatform.domain.Opportunity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Payloads for the opportunity (deal) resource. */
public final class OpportunityDtos {

    private OpportunityDtos() {}

    public record UpsertRequest(
            @NotNull Long accountId,
            @NotBlank @Size(max = 160) String name,
            @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Deal amount must be positive")
                    @Digits(integer = 13, fraction = 2)
                    BigDecimal amount,
            @NotNull Opportunity.Stage stage,
            @NotNull @Min(0) @Max(100) Integer probability,
            LocalDate expectedCloseDate,
            Long ownerUserId) {}

    /** Body for the kanban board's drag-and-drop stage change. */
    public record StageChangeRequest(@NotNull Opportunity.Stage stage, @Min(0) @Max(100) Integer probability) {}

    public record Response(
            Long id,
            Long accountId,
            String accountName,
            String name,
            BigDecimal amount,
            BigDecimal weightedAmount,
            Opportunity.Stage stage,
            int probability,
            LocalDate expectedCloseDate,
            Long ownerUserId,
            String ownerName,
            Instant createdAt,
            Instant updatedAt) {}
}

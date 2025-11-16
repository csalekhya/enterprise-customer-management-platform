package io.crmplatform.web.dto;

import io.crmplatform.domain.Account;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Payloads for the account (company) resource. */
public final class AccountDtos {

    private AccountDtos() {}

    /**
     * Create/update body. {@code ownerUserId} is optional: when omitted the caller becomes the
     * owner, which is what a sales rep filing their own account expects.
     */
    public record UpsertRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull Account.Industry industry,
            @NotNull Account.Tier tier,
            @DecimalMin(value = "0.0", message = "Annual revenue cannot be negative")
                    @Digits(integer = 13, fraction = 2)
                    BigDecimal annualRevenue,
            @Size(max = 255)
                    @Pattern(
                            regexp = "^$|^https?://.+",
                            message = "Website must be an http(s) URL")
                    String website,
            @NotNull Account.Status status,
            Long ownerUserId) {}

    public record Response(
            Long id,
            String name,
            Account.Industry industry,
            Account.Tier tier,
            BigDecimal annualRevenue,
            String website,
            Account.Status status,
            Long ownerUserId,
            String ownerName,
            long contactCount,
            long openOpportunityCount,
            Instant createdAt,
            Instant updatedAt) {

        /**
         * Returns a copy carrying the related-record counts.
         *
         * <p>The counts come from two aggregate queries rather than from mapped collections: an
         * account can own thousands of contacts, and nobody wants them loaded to print a number.
         */
        public Response withCounts(long contacts, long openOpportunities) {
            return new Response(
                    id,
                    name,
                    industry,
                    tier,
                    annualRevenue,
                    website,
                    status,
                    ownerUserId,
                    ownerName,
                    contacts,
                    openOpportunities,
                    createdAt,
                    updatedAt);
        }
    }

    /** Lightweight shape for pickers and nested references. */
    public record Summary(Long id, String name, Account.Tier tier) {}
}

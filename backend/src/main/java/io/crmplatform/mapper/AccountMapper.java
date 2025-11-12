package io.crmplatform.mapper;

import io.crmplatform.domain.Account;
import io.crmplatform.web.dto.AccountDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(target = "ownerUserId", source = "ownerUser.id")
    @Mapping(target = "ownerName", expression = "java(entity.getOwnerUser().fullName())")
    @Mapping(target = "contactCount", ignore = true)
    @Mapping(target = "openOpportunityCount", ignore = true)
    AccountDtos.Response toResponse(Account entity);

    AccountDtos.Summary toSummary(Account entity);

    /**
     * Applies an update in place. Identity, ownership and audit columns are deliberately excluded:
     * ownership changes are a separate, role-gated operation, and audit values belong to Spring
     * Data's listener.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerUser", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void apply(AccountDtos.UpsertRequest request, @MappingTarget Account entity);
}

package io.crmplatform.mapper;

import io.crmplatform.domain.Opportunity;
import io.crmplatform.web.dto.OpportunityDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface OpportunityMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountName", source = "account.name")
    @Mapping(target = "ownerUserId", source = "ownerUser.id")
    @Mapping(target = "ownerName", expression = "java(entity.getOwnerUser().fullName())")
    @Mapping(target = "weightedAmount", expression = "java(entity.weightedAmount())")
    OpportunityDtos.Response toResponse(Opportunity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "ownerUser", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void apply(OpportunityDtos.UpsertRequest request, @MappingTarget Opportunity entity);
}

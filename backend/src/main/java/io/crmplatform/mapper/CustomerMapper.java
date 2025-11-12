package io.crmplatform.mapper;

import io.crmplatform.domain.Customer;
import io.crmplatform.web.dto.CustomerDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountName", source = "account.name")
    @Mapping(target = "ownerUserId", source = "ownerUser.id")
    @Mapping(target = "ownerName", expression = "java(entity.getOwnerUser().fullName())")
    CustomerDtos.Response toResponse(Customer entity);

    /** Scalar fields only; account and owner are resolved by the service so they stay validated. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "ownerUser", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void apply(CustomerDtos.UpsertRequest request, @MappingTarget Customer entity);
}

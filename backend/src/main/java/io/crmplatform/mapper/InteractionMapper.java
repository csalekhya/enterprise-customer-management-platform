package io.crmplatform.mapper;

import io.crmplatform.domain.Interaction;
import io.crmplatform.web.dto.InteractionDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InteractionMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", expression = "java(entity.getCustomer().fullName())")
    @Mapping(target = "accountId", source = "customer.account.id")
    @Mapping(target = "accountName", source = "customer.account.name")
    @Mapping(target = "authorId", source = "createdByUser.id")
    @Mapping(target = "authorName", expression = "java(entity.getCreatedByUser().fullName())")
    InteractionDtos.Response toResponse(Interaction entity);
}

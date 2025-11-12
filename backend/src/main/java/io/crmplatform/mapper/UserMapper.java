package io.crmplatform.mapper;

import io.crmplatform.domain.Role;
import io.crmplatform.domain.User;
import io.crmplatform.web.dto.UserDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entity to DTO mapping for users.
 *
 * <p>MapStruct generates these at compile time, so the mapping cost is a field copy - no reflection
 * on the request path - and a renamed entity field breaks the build instead of silently returning
 * null in production.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDtos.Response toResponse(User entity);

    @Mapping(target = "name", expression = "java(entity.fullName())")
    UserDtos.Option toOption(User entity);

    default Set<Role.Name> toRoleNames(Set<Role> roles) {
        return roles == null
                ? Set.of()
                : roles.stream().map(Role::getName).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}

package io.crmplatform.repository;

import io.crmplatform.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(Role.Name name);

    List<Role> findByNameIn(Set<Role.Name> names);
}

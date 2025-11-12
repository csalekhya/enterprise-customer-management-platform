package io.crmplatform.repository;

import io.crmplatform.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Paged user directory with roles pre-joined.
     *
     * <p>{@code countQuery} is supplied explicitly because the fetch join would otherwise make
     * Hibernate count joined rows (one per role) instead of users.
     */
    @Query(
            value =
                    """
                    select distinct u from User u left join fetch u.roles
                    where (:search is null
                        or lower(u.email) like lower(concat('%', :search, '%'))
                        or lower(concat(u.firstName, ' ', u.lastName)) like lower(concat('%', :search, '%')))
                    """,
            countQuery =
                    """
                    select count(u) from User u
                    where (:search is null
                        or lower(u.email) like lower(concat('%', :search, '%'))
                        or lower(concat(u.firstName, ' ', u.lastName)) like lower(concat('%', :search, '%')))
                    """)
    Page<User> search(@Param("search") String search, Pageable pageable);
}

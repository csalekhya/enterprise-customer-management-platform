package io.crmplatform.service;

import io.crmplatform.domain.Account;
import io.crmplatform.domain.Customer;
import io.crmplatform.domain.Role;
import io.crmplatform.domain.User;
import io.crmplatform.exception.DuplicateResourceException;
import io.crmplatform.exception.ForbiddenOperationException;
import io.crmplatform.exception.ResourceNotFoundException;
import io.crmplatform.mapper.CustomerMapper;
import io.crmplatform.repository.AccountRepository;
import io.crmplatform.repository.CustomerRepository;
import io.crmplatform.repository.UserRepository;
import io.crmplatform.security.CrmUserDetails;
import io.crmplatform.security.CurrentUserProvider;
import io.crmplatform.web.dto.CustomerDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the contact service, with the repositories and the mapper stubbed.
 *
 * <p>The focus is the rule that cannot be expressed as an annotation: a SALES_REP holds exactly the
 * same authority as every other rep, so only a row-level owner comparison can keep them out of each
 * other's book of business. Each branch of that rule gets a test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerServiceTest {

    private static final long REP_ID = 7L;
    private static final long OTHER_REP_ID = 8L;
    private static final long MANAGER_ID = 3L;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private CustomerService customerService;

    private Account account;
    private User repUser;
    private Customer repOwnedContact;

    @BeforeEach
    void setUp() {
        repUser = user(REP_ID, "rep@slatecrm.io", "Marcus", "Ito");
        User otherRep = user(OTHER_REP_ID, "other@slatecrm.io", "Jo", "Fields");

        account = new Account();
        account.setId(100L);
        account.setName("Northwind Logistics");
        account.setTier(Account.Tier.PLATINUM);
        account.setStatus(Account.Status.ACTIVE);
        account.setOwnerUser(otherRep);

        repOwnedContact = contact(500L, repUser);

        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));
        when(userRepository.findById(REP_ID)).thenReturn(Optional.of(repUser));
        when(userRepository.findById(OTHER_REP_ID)).thenReturn(Optional.of(otherRep));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerMapper.toResponse(any(Customer.class))).thenAnswer(invocation -> {
            Customer source = invocation.getArgument(0);
            return response(source);
        });
    }

    @Nested
    @DisplayName("ownership enforcement")
    class OwnershipEnforcement {

        @Test
        @DisplayName("a rep may update a contact they own")
        void repCanUpdateOwnContact() {
            signedInAs(REP_ID, Role.Name.SALES_REP);
            when(customerRepository.findById(500L)).thenReturn(Optional.of(repOwnedContact));
            when(customerRepository.existsByEmailIgnoreCaseAndIdNot(anyString(), anyLong()))
                    .thenReturn(false);

            CustomerDtos.Response updated = customerService.update(500L, upsertRequest(null));

            assertThat(updated.id()).isEqualTo(500L);
            verify(customerMapper).apply(any(CustomerDtos.UpsertRequest.class), any(Customer.class));
        }

        @Test
        @DisplayName("a rep is refused a contact owned by someone else")
        void repCannotUpdateForeignContact() {
            signedInAs(REP_ID, Role.Name.SALES_REP);
            Customer foreign = contact(501L, user(OTHER_REP_ID, "other@slatecrm.io", "Jo", "Fields"));
            when(customerRepository.findById(501L)).thenReturn(Optional.of(foreign));

            assertThatThrownBy(() -> customerService.update(501L, upsertRequest(null)))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("only modify contacts that you own");

            verify(customerMapper, never()).apply(any(CustomerDtos.UpsertRequest.class), any(Customer.class));
        }

        @Test
        @DisplayName("a manager may update any contact")
        void managerBypassesOwnership() {
            signedInAs(MANAGER_ID, Role.Name.SALES_MANAGER);
            Customer foreign = contact(502L, user(OTHER_REP_ID, "other@slatecrm.io", "Jo", "Fields"));
            when(customerRepository.findById(502L)).thenReturn(Optional.of(foreign));
            when(customerRepository.existsByEmailIgnoreCaseAndIdNot(anyString(), anyLong()))
                    .thenReturn(false);

            CustomerDtos.Response updated = customerService.update(502L, upsertRequest(null));

            assertThat(updated.id()).isEqualTo(502L);
        }

        @Test
        @DisplayName("a rep cannot delete a contact owned by someone else")
        void repCannotDeleteForeignContact() {
            signedInAs(REP_ID, Role.Name.SALES_REP);
            Customer foreign = contact(503L, user(OTHER_REP_ID, "other@slatecrm.io", "Jo", "Fields"));
            when(customerRepository.findById(503L)).thenReturn(Optional.of(foreign));

            assertThatThrownBy(() -> customerService.delete(503L)).isInstanceOf(ForbiddenOperationException.class);

            verify(customerRepository, never()).delete(any(Customer.class));
        }

        @Test
        @DisplayName("a rep cannot hand a contact to another user")
        void repCannotReassignOwnership() {
            signedInAs(REP_ID, Role.Name.SALES_REP);

            assertThatThrownBy(() -> customerService.reassign(500L, OTHER_REP_ID))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("reassign ownership");
        }

        @Test
        @DisplayName("a manager can hand a contact to another user")
        void managerCanReassignOwnership() {
            signedInAs(MANAGER_ID, Role.Name.SALES_MANAGER);
            when(customerRepository.findById(500L)).thenReturn(Optional.of(repOwnedContact));

            CustomerDtos.Response reassigned = customerService.reassign(500L, OTHER_REP_ID);

            assertThat(reassigned.ownerUserId()).isEqualTo(OTHER_REP_ID);
        }
    }

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("a rep creating a contact becomes its owner")
        void creatorBecomesOwnerByDefault() {
            signedInAs(REP_ID, Role.Name.SALES_REP);
            when(customerRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

            CustomerDtos.Response created = customerService.create(upsertRequest(null));

            assertThat(created.ownerUserId()).isEqualTo(REP_ID);
            assertThat(created.accountId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("a rep cannot create a contact owned by someone else")
        void repCannotAssignForeignOwnerOnCreate() {
            signedInAs(REP_ID, Role.Name.SALES_REP);
            when(customerRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

            assertThatThrownBy(() -> customerService.create(upsertRequest(OTHER_REP_ID)))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        @DisplayName("a duplicate email is rejected before anything is written")
        void duplicateEmailIsRejected() {
            signedInAs(REP_ID, Role.Name.SALES_REP);
            when(customerRepository.existsByEmailIgnoreCase(anyString())).thenReturn(true);

            assertThatThrownBy(() -> customerService.create(upsertRequest(null)))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(customerRepository, never()).save(any(Customer.class));
        }

        @Test
        @DisplayName("an unknown account id surfaces as a not-found, not a constraint violation")
        void unknownAccountIsReportedAsNotFound() {
            signedInAs(REP_ID, Role.Name.SALES_REP);
            when(customerRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            CustomerDtos.UpsertRequest request = new CustomerDtos.UpsertRequest(
                    999L,
                    "Elena",
                    "Fischer",
                    "elena.fischer@example.com",
                    "+1-716-555-0142",
                    "VP Operations",
                    Customer.LifecycleStage.CHAMPION,
                    null,
                    "renewal");

            assertThatThrownBy(() -> customerService.create(request)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    private void signedInAs(long userId, Role.Name role) {
        CrmUserDetails principal =
                new CrmUserDetails(userId, "user-" + userId + "@slatecrm.io", "Test User", "", true, Set.of(role));
        when(currentUserProvider.require()).thenReturn(principal);
        when(currentUserProvider.requireId()).thenReturn(userId);
    }

    private CustomerDtos.UpsertRequest upsertRequest(Long ownerUserId) {
        return new CustomerDtos.UpsertRequest(
                100L,
                "Elena",
                "Fischer",
                "elena.fischer@example.com",
                "+1-716-555-0142",
                "VP Operations",
                Customer.LifecycleStage.CHAMPION,
                ownerUserId,
                "decision-maker");
    }

    private Customer contact(Long id, User owner) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setAccount(account);
        customer.setFirstName("Elena");
        customer.setLastName("Fischer");
        customer.setEmail("elena.fischer@example.com");
        customer.setLifecycleStage(Customer.LifecycleStage.CHAMPION);
        customer.setOwnerUser(owner);
        return customer;
    }

    private User user(Long id, String email, String firstName, String lastName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPasswordHash("{bcrypt}stub");
        user.setEnabled(true);
        user.setRoles(Set.of());
        return user;
    }

    private CustomerDtos.Response response(Customer source) {
        return new CustomerDtos.Response(
                source.getId(),
                source.getAccount() == null ? null : source.getAccount().getId(),
                source.getAccount() == null ? null : source.getAccount().getName(),
                source.getFirstName(),
                source.getLastName(),
                source.getEmail(),
                source.getPhone(),
                source.getJobTitle(),
                source.getLifecycleStage(),
                source.getOwnerUser() == null ? null : source.getOwnerUser().getId(),
                source.getOwnerUser() == null ? null : source.getOwnerUser().fullName(),
                source.getTags(),
                Instant.parse("2026-03-01T10:15:30Z"),
                Instant.parse("2026-03-01T10:15:30Z"));
    }
}

package io.crmplatform.service;

import io.crmplatform.domain.Account;
import io.crmplatform.domain.Customer;
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
import io.crmplatform.specification.CustomerSpecifications;
import io.crmplatform.web.dto.CustomerDtos;
import io.crmplatform.web.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contact use cases.
 *
 * <p>This is where record-level authorization lives. Spring Security's {@code @PreAuthorize} on the
 * controller answers "may this role call this endpoint at all"; only the service has the row in
 * hand and can answer "may this user touch <em>this</em> row". Putting the ownership rule here
 * means it holds no matter which entry point reaches it - REST today, a batch import tomorrow.
 *
 * <p>Transaction boundaries are drawn at this layer: one public method is one unit of work. Reads
 * are marked {@code readOnly} so Hibernate skips dirty checking and the driver can route them to a
 * replica when one exists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CustomerMapper customerMapper;
    private final CurrentUserProvider currentUserProvider;

    public PageResponse<CustomerDtos.Response> list(
            String search,
            Customer.LifecycleStage stage,
            Long accountId,
            Long ownerUserId,
            String tag,
            Account.Tier tier,
            Pageable pageable) {

        Specification<Customer> specification =
                CustomerSpecifications.build(search, stage, accountId, ownerUserId, tag, tier);
        Page<Customer> page = customerRepository.findAll(specification, pageable);
        return PageResponse.from(page, customerMapper::toResponse);
    }

    public CustomerDtos.Response get(Long id) {
        return customerMapper.toResponse(loadCustomer(id));
    }

    @Transactional
    public CustomerDtos.Response create(CustomerDtos.UpsertRequest request) {
        if (customerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A contact with email %s already exists".formatted(request.email()));
        }

        CrmUserDetails principal = currentUserProvider.require();
        Customer customer = new Customer();
        customerMapper.apply(request, customer);
        customer.setAccount(loadAccount(request.accountId()));
        customer.setOwnerUser(resolveOwner(request.ownerUserId(), principal));

        Customer saved = customerRepository.save(customer);
        log.info("Contact {} created on account {} by {}", saved.getId(), request.accountId(), principal.email());
        return customerMapper.toResponse(saved);
    }

    @Transactional
    public CustomerDtos.Response update(Long id, CustomerDtos.UpsertRequest request) {
        Customer customer = loadCustomer(id);
        assertCanMutate(customer);

        if (customerRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new DuplicateResourceException(
                    "Another contact already uses email %s".formatted(request.email()));
        }

        customerMapper.apply(request, customer);
        if (!customer.getAccount().getId().equals(request.accountId())) {
            customer.setAccount(loadAccount(request.accountId()));
        }
        // Re-parenting a contact to a different owner is an ownership transfer, not an edit, so it
        // is only honoured for callers allowed to bypass ownership.
        if (request.ownerUserId() != null
                && !request.ownerUserId().equals(customer.getOwnerUser().getId())) {
            assertCanReassign();
            customer.setOwnerUser(loadUser(request.ownerUserId()));
        }

        return customerMapper.toResponse(customer);
    }

    @Transactional
    public CustomerDtos.Response patch(Long id, CustomerDtos.PatchRequest request) {
        Customer customer = loadCustomer(id);
        assertCanMutate(customer);

        if (request.lifecycleStage() != null) {
            customer.setLifecycleStage(request.lifecycleStage());
        }
        if (request.tags() != null) {
            customer.setTags(request.tags().isBlank() ? null : request.tags());
        }
        return customerMapper.toResponse(customer);
    }

    /** Ownership transfer; gated to ADMIN and SALES_MANAGER at both the controller and here. */
    @Transactional
    public CustomerDtos.Response reassign(Long id, Long newOwnerUserId) {
        assertCanReassign();
        Customer customer = loadCustomer(id);
        customer.setOwnerUser(loadUser(newOwnerUserId));
        log.info("Contact {} reassigned to user {}", id, newOwnerUserId);
        return customerMapper.toResponse(customer);
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = loadCustomer(id);
        assertCanMutate(customer);
        // Interactions cascade at the database level; the timeline of a deleted contact has no
        // meaning on its own.
        customerRepository.delete(customer);
        log.info("Contact {} deleted by {}", id, currentUserProvider.require().email());
    }

    /**
     * Record-level guard.
     *
     * <p>ADMIN and SALES_MANAGER pass unconditionally. Everyone else must own the row - which is
     * what stops one sales rep from editing another's book of business even though both hold the
     * same role and pass the same {@code @PreAuthorize} check.
     */
    private void assertCanMutate(Customer customer) {
        CrmUserDetails principal = currentUserProvider.require();
        if (principal.canBypassOwnership()) {
            return;
        }
        if (!customer.getOwnerUser().getId().equals(principal.id())) {
            log.warn(
                    "Ownership check failed: user {} attempted to modify contact {} owned by {}",
                    principal.id(),
                    customer.getId(),
                    customer.getOwnerUser().getId());
            throw new ForbiddenOperationException("You can only modify contacts that you own");
        }
    }

    private void assertCanReassign() {
        if (!currentUserProvider.require().canBypassOwnership()) {
            throw new ForbiddenOperationException("Only managers and administrators can reassign ownership");
        }
    }

    private User resolveOwner(Long requestedOwnerId, CrmUserDetails principal) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.id())) {
            return loadUser(principal.id());
        }
        assertCanReassign();
        return loadUser(requestedOwnerId);
    }

    private Customer loadCustomer(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Contact", id));
    }

    private Account loadAccount(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account", id));
    }

    private User loadUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}

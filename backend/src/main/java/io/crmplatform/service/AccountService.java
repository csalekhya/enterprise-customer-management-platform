package io.crmplatform.service;

import io.crmplatform.domain.Account;
import io.crmplatform.domain.User;
import io.crmplatform.exception.DuplicateResourceException;
import io.crmplatform.exception.ForbiddenOperationException;
import io.crmplatform.exception.ResourceNotFoundException;
import io.crmplatform.mapper.AccountMapper;
import io.crmplatform.repository.AccountRepository;
import io.crmplatform.repository.CustomerRepository;
import io.crmplatform.repository.OpportunityRepository;
import io.crmplatform.repository.UserRepository;
import io.crmplatform.security.CrmUserDetails;
import io.crmplatform.security.CurrentUserProvider;
import io.crmplatform.specification.AccountSpecifications;
import io.crmplatform.web.dto.AccountDtos;
import io.crmplatform.web.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Account (company) use cases, with the same ownership contract as {@link CustomerService}. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final OpportunityRepository opportunityRepository;
    private final UserRepository userRepository;
    private final AccountMapper accountMapper;
    private final CurrentUserProvider currentUserProvider;

    public PageResponse<AccountDtos.Response> list(
            String search,
            Account.Tier tier,
            Account.Status status,
            Account.Industry industry,
            Long ownerUserId,
            BigDecimal minRevenue,
            Pageable pageable) {

        Specification<Account> specification =
                AccountSpecifications.build(search, tier, status, industry, ownerUserId, minRevenue);
        Page<Account> page = accountRepository.findAll(specification, pageable);
        // List rows skip the related-record counts on purpose: two aggregates per row would turn a
        // 25-row page into 51 queries. The detail endpoint pays that cost for exactly one account.
        return PageResponse.from(page, accountMapper::toResponse);
    }

    public AccountDtos.Response get(Long id) {
        Account account = loadAccount(id);
        return accountMapper
                .toResponse(account)
                .withCounts(customerRepository.countByAccountId(id), opportunityRepository.countByAccountId(id));
    }

    @Transactional
    public AccountDtos.Response create(AccountDtos.UpsertRequest request) {
        if (accountRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("An account named '%s' already exists".formatted(request.name()));
        }

        CrmUserDetails principal = currentUserProvider.require();
        Account account = new Account();
        accountMapper.apply(request, account);
        account.setOwnerUser(resolveOwner(request.ownerUserId(), principal));

        Account saved = accountRepository.save(account);
        log.info("Account {} ('{}') created by {}", saved.getId(), saved.getName(), principal.email());
        return accountMapper.toResponse(saved).withCounts(0, 0);
    }

    @Transactional
    public AccountDtos.Response update(Long id, AccountDtos.UpsertRequest request) {
        Account account = loadAccount(id);
        assertCanMutate(account);

        accountMapper.apply(request, account);
        if (request.ownerUserId() != null
                && !request.ownerUserId().equals(account.getOwnerUser().getId())) {
            assertCanReassign();
            account.setOwnerUser(loadUser(request.ownerUserId()));
        }

        return accountMapper
                .toResponse(account)
                .withCounts(customerRepository.countByAccountId(id), opportunityRepository.countByAccountId(id));
    }

    @Transactional
    public AccountDtos.Response reassign(Long id, Long newOwnerUserId) {
        assertCanReassign();
        Account account = loadAccount(id);
        account.setOwnerUser(loadUser(newOwnerUserId));
        log.info("Account {} reassigned to user {}", id, newOwnerUserId);
        return accountMapper.toResponse(account).withCounts(0, 0);
    }

    @Transactional
    public void delete(Long id) {
        Account account = loadAccount(id);
        assertCanMutate(account);

        // Accounts are the aggregate root: deleting one with live children would orphan pipeline
        // history, so the caller must clean up (or archive) first. The FK would refuse anyway -
        // failing here produces a message a human can act on.
        long contacts = customerRepository.countByAccountId(id);
        long deals = opportunityRepository.countByAccountId(id);
        if (contacts > 0 || deals > 0) {
            throw new ForbiddenOperationException(
                    "Account still has %d contact(s) and %d opportunity(ies). Reassign or remove them first."
                            .formatted(contacts, deals));
        }

        accountRepository.delete(account);
        log.info("Account {} deleted by {}", id, currentUserProvider.require().email());
    }

    private void assertCanMutate(Account account) {
        CrmUserDetails principal = currentUserProvider.require();
        if (principal.canBypassOwnership()) {
            return;
        }
        if (!account.getOwnerUser().getId().equals(principal.id())) {
            throw new ForbiddenOperationException("You can only modify accounts that you own");
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

    private Account loadAccount(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account", id));
    }

    private User loadUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}

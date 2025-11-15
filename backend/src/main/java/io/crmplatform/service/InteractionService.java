package io.crmplatform.service;

import io.crmplatform.domain.Customer;
import io.crmplatform.domain.Interaction;
import io.crmplatform.exception.ForbiddenOperationException;
import io.crmplatform.exception.ResourceNotFoundException;
import io.crmplatform.mapper.InteractionMapper;
import io.crmplatform.repository.CustomerRepository;
import io.crmplatform.repository.InteractionRepository;
import io.crmplatform.repository.UserRepository;
import io.crmplatform.security.CrmUserDetails;
import io.crmplatform.security.CurrentUserProvider;
import io.crmplatform.web.dto.InteractionDtos;
import io.crmplatform.web.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activity log use cases.
 *
 * <p>Interactions are append-only by policy: there is no update path, and deletion is restricted to
 * the author. An activity trail that anyone can rewrite is worth nothing during a deal review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InteractionService {

    private final InteractionRepository interactionRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final InteractionMapper interactionMapper;
    private final CurrentUserProvider currentUserProvider;

    /** Timeline, optionally scoped to one contact or one account. */
    public PageResponse<InteractionDtos.Response> list(Long customerId, Long accountId, Pageable pageable) {
        Page<Interaction> page;
        if (customerId != null) {
            page = interactionRepository.findByCustomerIdOrderByOccurredAtDesc(customerId, pageable);
        } else if (accountId != null) {
            page = interactionRepository.findByCustomerAccountIdOrderByOccurredAtDesc(accountId, pageable);
        } else {
            page = interactionRepository.findAllByOrderByOccurredAtDesc(pageable);
        }
        return PageResponse.from(page, interactionMapper::toResponse);
    }

    public InteractionDtos.Response get(Long id) {
        return interactionMapper.toResponse(loadInteraction(id));
    }

    @Transactional
    public InteractionDtos.Response create(InteractionDtos.CreateRequest request) {
        CrmUserDetails principal = currentUserProvider.require();
        Customer customer = customerRepository
                .findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Contact", request.customerId()));

        // Reps may log activity against any contact they can see - visibility is company wide and
        // recording a call should never be blocked by ownership. Only the author is recorded.
        Interaction interaction = new Interaction();
        interaction.setCustomer(customer);
        interaction.setType(request.type());
        interaction.setSubject(request.subject());
        interaction.setBody(request.body());
        interaction.setOccurredAt(request.occurredAt());
        interaction.setCreatedByUser(userRepository
                .findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.id())));

        Interaction saved = interactionRepository.save(interaction);
        log.info("Interaction {} ({}) logged on contact {}", saved.getId(), request.type(), customer.getId());
        return interactionMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Interaction interaction = loadInteraction(id);
        CrmUserDetails principal = currentUserProvider.require();
        boolean isAuthor = interaction.getCreatedByUser().getId().equals(principal.id());
        if (!isAuthor && !principal.canBypassOwnership()) {
            throw new ForbiddenOperationException("Only the author, a manager or an administrator can remove an entry");
        }
        interactionRepository.delete(interaction);
    }

    private Interaction loadInteraction(Long id) {
        return interactionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Interaction", id));
    }
}

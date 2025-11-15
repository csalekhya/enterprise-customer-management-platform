package io.crmplatform.service;

import io.crmplatform.domain.Account;
import io.crmplatform.domain.Opportunity;
import io.crmplatform.domain.User;
import io.crmplatform.exception.ForbiddenOperationException;
import io.crmplatform.exception.ResourceNotFoundException;
import io.crmplatform.mapper.OpportunityMapper;
import io.crmplatform.repository.AccountRepository;
import io.crmplatform.repository.OpportunityRepository;
import io.crmplatform.repository.UserRepository;
import io.crmplatform.security.CrmUserDetails;
import io.crmplatform.security.CurrentUserProvider;
import io.crmplatform.web.dto.OpportunityDtos;
import io.crmplatform.web.dto.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pipeline use cases, including the board read model the kanban view renders from. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpportunityService {

    private final OpportunityRepository opportunityRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final OpportunityMapper opportunityMapper;
    private final CurrentUserProvider currentUserProvider;

    public PageResponse<OpportunityDtos.Response> list(
            Opportunity.Stage stage, Long accountId, Long ownerUserId, Pageable pageable) {

        Specification<Opportunity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (stage != null) {
                predicates.add(cb.equal(root.get("stage"), stage));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("account").get("id"), accountId));
            }
            if (ownerUserId != null) {
                predicates.add(cb.equal(root.get("ownerUser").get("id"), ownerUserId));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<Opportunity> page = opportunityRepository.findAll(specification, pageable);
        return PageResponse.from(page, opportunityMapper::toResponse);
    }

    /**
     * Board read model: every stage, in enum order, with its deals.
     *
     * <p>Returns a map keyed by stage so the client renders columns without grouping client-side,
     * and so empty stages still produce a (drop-target) column.
     */
    public Map<Opportunity.Stage, List<OpportunityDtos.Response>> board() {
        Map<Opportunity.Stage, List<OpportunityDtos.Response>> board = new EnumMap<>(Opportunity.Stage.class);
        for (Opportunity.Stage stage : Opportunity.Stage.values()) {
            board.put(
                    stage,
                    opportunityRepository.findByStageOrderByExpectedCloseDateAsc(stage).stream()
                            .map(opportunityMapper::toResponse)
                            .toList());
        }
        return board;
    }

    public OpportunityDtos.Response get(Long id) {
        return opportunityMapper.toResponse(loadOpportunity(id));
    }

    @Transactional
    public OpportunityDtos.Response create(OpportunityDtos.UpsertRequest request) {
        CrmUserDetails principal = currentUserProvider.require();
        Opportunity opportunity = new Opportunity();
        opportunityMapper.apply(request, opportunity);
        opportunity.setAccount(loadAccount(request.accountId()));
        opportunity.setOwnerUser(resolveOwner(request.ownerUserId(), principal));

        Opportunity saved = opportunityRepository.save(opportunity);
        log.info("Opportunity {} ('{}') created by {}", saved.getId(), saved.getName(), principal.email());
        return opportunityMapper.toResponse(saved);
    }

    @Transactional
    public OpportunityDtos.Response update(Long id, OpportunityDtos.UpsertRequest request) {
        Opportunity opportunity = loadOpportunity(id);
        assertCanMutate(opportunity);

        opportunityMapper.apply(request, opportunity);
        if (!opportunity.getAccount().getId().equals(request.accountId())) {
            opportunity.setAccount(loadAccount(request.accountId()));
        }
        if (request.ownerUserId() != null
                && !request.ownerUserId().equals(opportunity.getOwnerUser().getId())) {
            assertCanReassign();
            opportunity.setOwnerUser(loadUser(request.ownerUserId()));
        }
        return opportunityMapper.toResponse(opportunity);
    }

    /**
     * Stage transition from the kanban board.
     *
     * <p>Closing a deal pins probability to the terminal value: a WON deal at 60% would quietly
     * corrupt every weighted forecast downstream.
     */
    @Transactional
    public OpportunityDtos.Response changeStage(Long id, OpportunityDtos.StageChangeRequest request) {
        Opportunity opportunity = loadOpportunity(id);
        assertCanMutate(opportunity);

        opportunity.setStage(request.stage());
        if (request.stage() == Opportunity.Stage.WON) {
            opportunity.setProbability(100);
        } else if (request.stage() == Opportunity.Stage.LOST) {
            opportunity.setProbability(0);
        } else if (request.probability() != null) {
            opportunity.setProbability(request.probability());
        }

        log.info("Opportunity {} moved to {}", id, request.stage());
        return opportunityMapper.toResponse(opportunity);
    }

    @Transactional
    public void delete(Long id) {
        Opportunity opportunity = loadOpportunity(id);
        assertCanMutate(opportunity);
        opportunityRepository.delete(opportunity);
    }

    private void assertCanMutate(Opportunity opportunity) {
        CrmUserDetails principal = currentUserProvider.require();
        if (principal.canBypassOwnership()) {
            return;
        }
        if (!opportunity.getOwnerUser().getId().equals(principal.id())) {
            throw new ForbiddenOperationException("You can only modify opportunities that you own");
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

    private Opportunity loadOpportunity(Long id) {
        return opportunityRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Opportunity", id));
    }

    private Account loadAccount(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account", id));
    }

    private User loadUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}

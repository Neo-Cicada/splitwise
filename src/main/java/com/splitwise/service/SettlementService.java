package com.splitwise.service;

import com.splitwise.domain.Group;
import com.splitwise.domain.GroupMember;
import com.splitwise.domain.Settlement;
import com.splitwise.domain.User;
import com.splitwise.repository.BalanceView;
import com.splitwise.repository.GroupMemberRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.settle.DebtSimplificationService;
import com.splitwise.settle.MemberBalance;
import com.splitwise.settle.SuggestedTransfer;
import com.splitwise.support.FieldValidationException;
import com.splitwise.support.NotFoundException;
import com.splitwise.web.form.CreateSettlementForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final BalanceService balanceService;
    private final DebtSimplificationService debtSimplificationService;
    private final AccessGuard accessGuard;

    public SettlementService(SettlementRepository settlementRepository,
                             GroupRepository groupRepository,
                             GroupMemberRepository groupMemberRepository,
                             BalanceService balanceService,
                             DebtSimplificationService debtSimplificationService,
                             AccessGuard accessGuard) {
        this.settlementRepository = settlementRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.balanceService = balanceService;
        this.debtSimplificationService = debtSimplificationService;
        this.accessGuard = accessGuard;
    }

    /** Payments that would clear the group. Suggestions only — nothing is written. */
    public List<SuggestedTransfer> suggestTransfers(Long groupId, Long actorId) {
        List<MemberBalance> balances = balanceService.balancesFor(groupId, actorId).stream()
                .map(view -> new MemberBalance(view.getUserId(), view.getName(), view.getNetBalance()))
                .toList();
        return debtSimplificationService.simplify(balances);
    }

    public List<Settlement> findByGroup(Long groupId, Long actorId) {
        accessGuard.requireActiveMember(groupId, actorId);
        return settlementRepository.findByGroupId(groupId);
    }

    /**
     * Records a payment that has already happened outside the app. Balances
     * shift as a consequence; nothing about the balance itself is stored.
     */
    @Transactional
    public Settlement record(Long groupId, CreateSettlementForm form, Long actorId) {
        accessGuard.requireActiveMember(groupId, actorId);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("No group with id " + groupId + "."));

        Map<Long, User> activeMembers = groupMemberRepository.findActiveByGroupId(groupId).stream()
                .map(GroupMember::getUser)
                .collect(Collectors.toMap(User::getId, Function.identity()));

        User payer = activeMembers.get(form.getFromUser());
        if (payer == null) {
            throw new FieldValidationException("fromUser", "The payer must be an active member of this group.");
        }

        User payee = activeMembers.get(form.getToUser());
        if (payee == null) {
            throw new FieldValidationException("toUser", "The recipient must be an active member of this group.");
        }

        if (payer.getId().equals(payee.getId())) {
            throw new FieldValidationException("toUser", "A member cannot settle up with themselves.");
        }

        // The form's @DecimalMin covers this too; the database CHECK is the
        // final backstop. Kept here so the service is safe when called directly.
        if (form.getAmount() == null || form.getAmount().signum() <= 0) {
            throw new FieldValidationException("amount", "The amount must be greater than zero.");
        }

        return settlementRepository.save(Settlement.builder()
                .group(group)
                .fromUser(payer)
                .toUser(payee)
                .amount(form.getAmount())
                .build());
    }

    /** Names for the confirmation message, resolved while the session is open. */
    @Transactional(readOnly = true)
    public String describe(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new NotFoundException("No settlement " + settlementId + "."));
        return settlement.getFromUser().getName() + " paid " + settlement.getToUser().getName();
    }

    /** Convenience for callers that already hold the balances. */
    public List<BalanceView> balances(Long groupId, Long actorId) {
        return balanceService.balancesFor(groupId, actorId);
    }
}

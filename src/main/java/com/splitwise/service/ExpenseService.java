package com.splitwise.service;

import com.splitwise.domain.Expense;
import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Group;
import com.splitwise.domain.GroupMember;
import com.splitwise.domain.User;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.ExpenseShareRepository;
import com.splitwise.repository.GroupMemberRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.UserShareView;
import com.splitwise.split.ShareDto;
import com.splitwise.split.SplitInput;
import com.splitwise.split.SplitStrategyResolver;
import com.splitwise.support.FieldValidationException;
import com.splitwise.support.NotFoundException;
import com.splitwise.web.form.CreateExpenseForm;
import com.splitwise.web.form.ParticipantInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SplitStrategyResolver splitStrategyResolver;
    private final AccessGuard accessGuard;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ExpenseShareRepository expenseShareRepository,
                          GroupRepository groupRepository,
                          GroupMemberRepository groupMemberRepository,
                          SplitStrategyResolver splitStrategyResolver,
                          AccessGuard accessGuard) {
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.splitStrategyResolver = splitStrategyResolver;
        this.accessGuard = accessGuard;
    }

    public List<Expense> findFeed(Long groupId, Long actorId) {
        accessGuard.requireActiveMember(groupId, actorId);
        return expenseRepository.findActiveByGroupId(groupId);
    }

    /** Expense id to the given user's share, for the feed's "your share" column. */
    public Map<Long, BigDecimal> findSharesFor(Long groupId, Long userId) {
        accessGuard.requireActiveMember(groupId, userId);
        return expenseShareRepository.findSharesFor(groupId, userId).stream()
                .collect(Collectors.toMap(UserShareView::expenseId, UserShareView::amount));
    }

    /**
     * Validates the form against the group's live membership, computes the
     * shares, and stores the expense with its shares in one transaction. Rule
     * violations come back as {@link FieldValidationException} so the
     * controller can attach them to the field that caused them.
     */
    @Transactional
    public Expense create(Long groupId, CreateExpenseForm form, Long actorId) {
        accessGuard.requireActiveMember(groupId, actorId);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("No group with id " + groupId + "."));

        Map<Long, User> activeMembers = groupMemberRepository.findActiveByGroupId(groupId).stream()
                .map(GroupMember::getUser)
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        User payer = activeMembers.get(form.getPaidBy());
        if (payer == null) {
            throw new FieldValidationException("paidBy", "The payer must be an active member of this group.");
        }

        User actor = activeMembers.get(actorId);
        if (actor == null) {
            throw new FieldValidationException(null, "You are not an active member of this group.");
        }

        List<ParticipantInput> selected = selectedParticipants(form, activeMembers);

        List<SplitInput> splitInputs = selected.stream()
                .map(p -> new SplitInput(p.getUserId(), p.getValue()))
                .toList();

        // Throws InvalidSplitException (also a BusinessRuleException) when the
        // amounts or percentages do not work out; the controller maps it onto
        // the participants field.
        List<ShareDto> shares = splitStrategyResolver.split(
                form.getSplitType(), form.getAmount(), splitInputs);

        BigDecimal total = shares.stream()
                .map(ShareDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(form.getAmount()) != 0) {
            throw new IllegalStateException(
                    "Shares sum to " + total.toPlainString() + " but the expense is "
                            + form.getAmount().toPlainString() + ".");
        }

        Expense expense = expenseRepository.save(Expense.builder()
                .group(group)
                .paidBy(payer)
                .amount(form.getAmount())
                .description(trimToNull(form.getDescription()))
                .splitType(form.getSplitType())
                .spentAt(form.getSpentAt())
                .createdBy(actor)
                .build());

        List<ExpenseShare> rows = shares.stream()
                .map(share -> ExpenseShare.builder()
                        .expense(expense)
                        .user(activeMembers.get(share.userId()))
                        .shareAmount(share.amount())
                        .build())
                .toList();
        expenseShareRepository.saveAll(rows);

        return expense;
    }

    /** Soft delete: the row and its shares stay, so history and balances survive. */
    @Transactional
    public Long softDelete(Long expenseId, Long actorId) {
        Expense expense = accessGuard.requireCanDeleteExpense(expenseId, actorId);

        if (expense.getDeletedAt() == null) {
            expense.setDeletedAt(OffsetDateTime.now());
        }
        return expense.getGroup().getId();
    }

    private List<ParticipantInput> selectedParticipants(CreateExpenseForm form,
                                                        Map<Long, User> activeMembers) {
        List<ParticipantInput> selected = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        for (ParticipantInput participant : form.getParticipants()) {
            if (!participant.isSelected()) {
                continue;
            }
            if (participant.getUserId() == null || !activeMembers.containsKey(participant.getUserId())) {
                throw new FieldValidationException("participants",
                        "Only active members of this group can be part of an expense.");
            }
            if (!seen.add(participant.getUserId())) {
                throw new FieldValidationException("participants",
                        "The same person is listed twice.");
            }
            selected.add(participant);
        }

        if (selected.isEmpty()) {
            throw new FieldValidationException("participants", "Select at least one participant.");
        }
        return selected;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

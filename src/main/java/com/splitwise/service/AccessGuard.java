package com.splitwise.service;

import com.splitwise.domain.Expense;
import com.splitwise.domain.GroupMember;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.GroupMemberRepository;
import com.splitwise.support.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every authorization decision in the application. Keeping it in one service
 * means the rules cannot drift apart across controllers, and the same check
 * that guards an action also answers whether to render its button.
 */
@Service
@Transactional(readOnly = true)
public class AccessGuard {

    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;

    public AccessGuard(GroupMemberRepository groupMemberRepository,
                       ExpenseRepository expenseRepository) {
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
    }

    /**
     * The reusable guard: only an active member may touch a group at all.
     *
     * <p>A member who has left is treated as an outsider — they keep their
     * history in the ledger but lose access to the group.
     *
     * @return the caller's membership, so callers can inspect the role
     */
    public GroupMember requireActiveMember(Long groupId, Long userId) {
        return groupMemberRepository.findMembership(groupId, userId)
                .filter(membership -> membership.getLeftAt() == null)
                .orElseThrow(() -> new AccessDeniedException(
                        "You are not a member of this group."));
    }

    /**
     * An expense may be deleted by whoever recorded it, or by any group admin.
     */
    public Expense requireCanDeleteExpense(Long expenseId, Long userId) {
        Expense expense = expenseRepository.findByIdWithGroup(expenseId)
                .orElseThrow(() -> new NotFoundException("No expense with id " + expenseId + "."));

        GroupMember membership = requireActiveMember(expense.getGroup().getId(), userId);

        boolean isCreator = expense.getCreatedBy().getId().equals(userId);
        boolean isAdmin = GroupService.ROLE_ADMIN.equals(membership.getRole());
        if (!isCreator && !isAdmin) {
            throw new AccessDeniedException(
                    "Only the person who added an expense, or a group admin, can delete it.");
        }
        return expense;
    }

    /** Same rule as {@link #requireCanDeleteExpense}, as a question. */
    public boolean canDeleteExpense(Long expenseId, Long userId) {
        try {
            requireCanDeleteExpense(expenseId, userId);
            return true;
        } catch (AccessDeniedException | NotFoundException e) {
            return false;
        }
    }
}

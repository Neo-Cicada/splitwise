package com.splitwise.repository;

import com.splitwise.domain.GroupMember;
import com.splitwise.domain.GroupMemberId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BalanceRepository extends Repository<GroupMember, GroupMemberId> {

    /**
     * Derives every member's net position in one pass.
     *
     * <p>Four CTEs aggregate the four ways money moves, then all four are LEFT
     * JOINed onto group_members so a member with no activity still comes back
     * as 0.00 rather than dropping out of the result or arriving as null.
     *
     * <p>Members who have left are deliberately included: they are still owed
     * money, or still owe it, and excluding them would break the zero-sum
     * invariant the whole model rests on.
     *
     * <p>Column aliases are quoted camelCase so they bind to BalanceView's
     * getters exactly, with no snake_case guessing in between.
     */
    @Query(value = """
            WITH paid AS (
                SELECT paid_by AS user_id, SUM(amount) AS total
                FROM expenses
                WHERE group_id = :groupId AND deleted_at IS NULL
                GROUP BY paid_by
            ),
            owed AS (
                SELECT es.user_id AS user_id, SUM(es.share_amount) AS total
                FROM expense_shares es
                JOIN expenses e ON e.id = es.expense_id
                WHERE e.group_id = :groupId AND e.deleted_at IS NULL
                GROUP BY es.user_id
            ),
            sent AS (
                SELECT from_user AS user_id, SUM(amount) AS total
                FROM settlements
                WHERE group_id = :groupId
                GROUP BY from_user
            ),
            received AS (
                SELECT to_user AS user_id, SUM(amount) AS total
                FROM settlements
                WHERE group_id = :groupId
                GROUP BY to_user
            )
            SELECT u.id AS "userId",
                   u.name AS "name",
                   ROUND(
                       COALESCE(p.total, 0) - COALESCE(o.total, 0)
                     + COALESCE(s.total, 0) - COALESCE(r.total, 0)
                   , 2) AS "netBalance"
            FROM group_members gm
            JOIN "users" u ON u.id = gm.user_id
            LEFT JOIN paid p ON p.user_id = gm.user_id
            LEFT JOIN owed o ON o.user_id = gm.user_id
            LEFT JOIN sent s ON s.user_id = gm.user_id
            LEFT JOIN received r ON r.user_id = gm.user_id
            WHERE gm.group_id = :groupId
            ORDER BY "netBalance" DESC, u.name ASC
            """, nativeQuery = true)
    List<BalanceView> findBalances(@Param("groupId") Long groupId);
}

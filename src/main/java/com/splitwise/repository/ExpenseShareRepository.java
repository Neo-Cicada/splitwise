package com.splitwise.repository;

import com.splitwise.domain.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {

    /** One user's share of each active expense in a group. */
    @Query("""
            select new com.splitwise.repository.UserShareView(s.expense.id, s.shareAmount)
            from ExpenseShare s
            where s.expense.group.id = :groupId
              and s.user.id = :userId
              and s.expense.deletedAt is null
            """)
    List<UserShareView> findSharesFor(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Query("select coalesce(sum(s.shareAmount), 0) from ExpenseShare s where s.expense.id = :expenseId")
    BigDecimal sumSharesFor(@Param("expenseId") Long expenseId);
}

package com.splitwise.repository;

import com.splitwise.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /**
     * The group's expense feed, newest first. Soft-deleted rows are excluded,
     * which is what the partial index on expenses(group_id) is built for.
     */
    @Query("""
            select e from Expense e
            join fetch e.paidBy
            where e.group.id = :groupId and e.deletedAt is null
            order by e.spentAt desc, e.createdAt desc
            """)
    List<Expense> findActiveByGroupId(@Param("groupId") Long groupId);

    @Query("select e from Expense e join fetch e.group where e.id = :id")
    Optional<Expense> findByIdWithGroup(@Param("id") Long id);
}

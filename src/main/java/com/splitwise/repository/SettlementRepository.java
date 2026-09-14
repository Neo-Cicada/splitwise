package com.splitwise.repository;

import com.splitwise.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @Query("""
            select s from Settlement s
            join fetch s.fromUser
            join fetch s.toUser
            where s.group.id = :groupId
            order by s.settledAt desc
            """)
    List<Settlement> findByGroupId(@Param("groupId") Long groupId);
}

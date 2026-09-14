package com.splitwise.repository;

import com.splitwise.domain.GroupMember;
import com.splitwise.domain.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    /** Active members, with the user fetched for template rendering. */
    @Query("""
            select gm from GroupMember gm
            join fetch gm.user
            where gm.group.id = :groupId and gm.leftAt is null
            order by gm.joinedAt
            """)
    List<GroupMember> findActiveByGroupId(@Param("groupId") Long groupId);

    @Query("select gm from GroupMember gm where gm.group.id = :groupId and gm.user.id = :userId")
    Optional<GroupMember> findMembership(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Query("""
            select count(gm) from GroupMember gm
            where gm.group.id = :groupId and gm.leftAt is null and gm.role = 'ADMIN'
            """)
    long countActiveAdmins(@Param("groupId") Long groupId);
}

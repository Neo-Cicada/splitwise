package com.splitwise.repository;

import com.splitwise.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * Groups the user is currently a member of. open-in-view is off, so the
     * creator is fetched eagerly here rather than lazily in the template.
     */
    @Query("""
            select g from Group g
            join fetch g.createdBy
            where exists (
                select 1 from GroupMember gm
                where gm.group = g and gm.user.id = :userId and gm.leftAt is null
            )
            order by g.createdAt desc
            """)
    List<Group> findActiveMembershipsFor(@Param("userId") Long userId);

    @Query("select g from Group g join fetch g.createdBy where g.id = :id")
    Optional<Group> findByIdWithCreator(@Param("id") Long id);
}

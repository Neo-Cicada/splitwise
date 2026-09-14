package com.splitwise.service;

import com.splitwise.domain.Group;
import com.splitwise.domain.GroupMember;
import com.splitwise.domain.User;
import com.splitwise.repository.GroupMemberRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.UserRepository;
import com.splitwise.support.BusinessRuleException;
import com.splitwise.support.NotFoundException;
import com.splitwise.web.form.CreateGroupForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Business rules for groups and membership. Controllers stay free of logic;
 * transaction boundaries live here.
 */
@Service
@Transactional(readOnly = true)
public class GroupService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository,
                        AccessGuard accessGuard) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
    }

    public List<Group> findGroupsFor(Long userId) {
        return groupRepository.findActiveMembershipsFor(userId);
    }

    public Group getGroup(Long groupId, Long actorId) {
        accessGuard.requireActiveMember(groupId, actorId);
        return groupRepository.findByIdWithCreator(groupId)
                .orElseThrow(() -> new NotFoundException("No group with id " + groupId + "."));
    }

    public List<GroupMember> findActiveMembers(Long groupId, Long actorId) {
        accessGuard.requireActiveMember(groupId, actorId);
        return groupMemberRepository.findActiveByGroupId(groupId);
    }

    /** Creates the group and auto-joins the creator as ADMIN. */
    @Transactional
    public Group create(CreateGroupForm form, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new NotFoundException("No user with id " + creatorId + "."));

        Group group = groupRepository.save(Group.builder()
                .name(form.getName().trim())
                .currency(form.getCurrency().toUpperCase())
                .createdBy(creator)
                .build());

        groupMemberRepository.save(GroupMember.builder()
                .group(group)
                .user(creator)
                .role(ROLE_ADMIN)
                .build());

        return group;
    }

    /**
     * Adds an existing user to the group by email. Someone who previously left
     * rejoins by clearing left_at, so their history stays on the same row.
     *
     * @return the added member's display name, resolved inside the transaction
     */
    @Transactional
    public String addMember(Long groupId, String email, Long actorId) {
        accessGuard.requireActiveMember(groupId, actorId);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("No group with id " + groupId + "."));

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new BusinessRuleException(
                        "No user is registered with " + email.trim() + "."));

        groupMemberRepository.findMembership(groupId, user.getId()).ifPresentOrElse(
                existing -> {
                    if (existing.getLeftAt() == null) {
                        throw new BusinessRuleException(
                                user.getName() + " is already in this group.");
                    }
                    existing.setLeftAt(null);
                    existing.setRole(ROLE_MEMBER);
                },
                () -> groupMemberRepository.save(GroupMember.builder()
                        .group(group)
                        .user(user)
                        .role(ROLE_MEMBER)
                        .build()));

        return user.getName();
    }

    /**
     * Marks a membership as ended. The row is never deleted — expenses and
     * settlements still reference the user, and the history has to survive.
     *
     * @return the departing member's display name, resolved inside the
     *         transaction — returning the entity would hand the controller a
     *         lazy proxy that can no longer initialise.
     */
    @Transactional
    public String removeMember(Long groupId, Long userId, Long actorId) {
        accessGuard.requireActiveMember(groupId, actorId);
        GroupMember membership = groupMemberRepository.findMembership(groupId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "User " + userId + " is not a member of group " + groupId + "."));

        if (membership.getLeftAt() != null) {
            throw new BusinessRuleException("That member has already left the group.");
        }
        if (ROLE_ADMIN.equals(membership.getRole()) && groupMemberRepository.countActiveAdmins(groupId) <= 1) {
            throw new BusinessRuleException("A group needs at least one admin.");
        }

        membership.setLeftAt(OffsetDateTime.now());
        return membership.getUser().getName();
    }
}

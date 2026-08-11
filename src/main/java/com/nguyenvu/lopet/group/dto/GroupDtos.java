package com.nguyenvu.lopet.group.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nguyenvu.lopet.account.dto.AccountViews.AccountBrief;
import com.nguyenvu.lopet.group.entity.GroupMemberRole;
import com.nguyenvu.lopet.group.entity.GroupType;

public final class GroupDtos {

    public record CreateGroupRequest(String name, String type, String bio) {
    }

    public record CreateGroupResponse(
            Integer id,
            String name,
            GroupType type,
            Integer owner,
            String bio,
            String coverUrl,
            LocalDateTime createdAt) {
    }

    public record AddMemberRequest(Integer groupId, Integer invitee) {
    }

    public record AddMemberResponse(Integer groupId, Integer invitee) {
    }

    public record RemoveMemberRequest(Integer groupId, Integer member) {
    }

    public record RemoveMemberResponse(Integer groupId, Integer member) {
    }

    public record DeleteGroupRequest(Integer groupId) {
    }

    /** {@code owner} không bao giờ được gán bên TS nên khoá đó vắng mặt trong JSON */
    public record DeleteGroupResponse(Integer groupId) {
    }

    public record ModifyGroupRequest(String name, String type, String bio) {
    }

    public record ModifyGroupResponse(Integer id, boolean success) {
    }

    /** Dùng chung cho owned / joined / suggest — cả ba đều dựng cùng một bộ trường */
    public record GroupSummary(
            Integer id,
            String name,
            Integer ownerId,
            String bio,
            String coverUrl,
            GroupType type,
            Integer totalMembers,
            LocalDateTime createdAt) {
    }

    /** GET /v1/groups/:id trả thẳng entity kèm {@code members.account} */
    public record GroupDetail(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer id,
            String name,
            GroupType type,
            String bio,
            String coverUrl,
            List<GroupMemberView> members) {
    }

    /** Quan hệ {@code group} không được nạp kèm bên TS nên khoá đó vắng mặt */
    public record GroupMemberView(
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt,
            Integer groupId,
            Integer accountId,
            AccountBrief account,
            GroupMemberRole role,
            LocalDateTime joinedAt) {
    }

    private GroupDtos() {
    }
}

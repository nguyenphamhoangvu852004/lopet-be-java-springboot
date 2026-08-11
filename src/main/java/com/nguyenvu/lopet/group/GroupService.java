package com.nguyenvu.lopet.group;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.AccountMapper;
import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.group.dto.GroupDtos;
import com.nguyenvu.lopet.group.entity.Group;
import com.nguyenvu.lopet.group.entity.GroupMember;
import com.nguyenvu.lopet.group.entity.GroupMemberRole;
import com.nguyenvu.lopet.group.entity.GroupType;
import com.nguyenvu.lopet.group.repository.GroupMemberRepository;
import com.nguyenvu.lopet.group.repository.GroupRepository;

import lombok.RequiredArgsConstructor;

/**
 * Quyền quản trị nhóm nằm ở {@code group_members.role}, không phải ở role toàn cục — đó là lý do
 * mọi kiểm tra ở đây đi qua {@link #canManage} / {@link #isOwner} chứ không qua permission.
 *
 * <p>Hai mức khác nhau và không được lẫn: {@code canManage} (OWNER hoặc ADMIN) cho thao tác quản
 * trị thường ngày, còn {@code isOwned} (đúng OWNER) cho hành động huỷ diệt là xoá nhóm.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public GroupDtos.CreateGroupResponse create(String name, String type, String bio, String coverUrl, Integer ownerId) {
        Account account = accountRepository.findById(ownerId).orElseThrow(BadRequestException::new);

        Group group = groupRepository.save(Group.builder()
                .name(name)
                // Bất kỳ giá trị nào khác chuỗi 'PUBLIC' đều thành PRIVATE — giữ nguyên phép so
                // sánh của controller TS, kể cả khi client gửi thiếu hoặc gửi rác.
                .type("PUBLIC".equals(type) ? GroupType.PUBLIC : GroupType.PRIVATE)
                .bio(bio == null ? "" : bio)
                .coverUrl(coverUrl == null ? "" : coverUrl)
                .build());

        // Người tạo trở thành OWNER trong group_members thay cho cột groups.owner cũ
        groupMemberRepository.save(GroupMember.builder()
                .groupId(group.getId())
                .accountId(account.getId())
                .role(GroupMemberRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build());

        return new GroupDtos.CreateGroupResponse(group.getId(), group.getName(), group.getType(),
                account.getId(), group.getBio(), group.getCoverUrl(), group.getCreatedAt());
    }

    @Transactional
    public GroupDtos.AddMemberResponse addMember(Integer groupId, Integer inviteeId, Integer callerId) {
        requireManager(groupId, callerId);

        Account invitee = accountRepository.findById(inviteeId).orElseThrow(BadRequestException::new);
        Group group = groupRepository.findById(groupId).orElseThrow(BadRequestException::new);

        if (groupMemberRepository.findByGroupIdAndAccountId(groupId, invitee.getId()).isPresent()) {
            throw new BadRequestException("Thành viên đã ở trong nhóm");
        }

        groupMemberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .accountId(invitee.getId())
                .role(GroupMemberRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build());

        return new GroupDtos.AddMemberResponse(group.getId(), invitee.getId());
    }

    @Transactional
    public GroupDtos.RemoveMemberResponse removeMember(Integer groupId, Integer memberId, Integer callerId) {
        requireManager(groupId, callerId);

        GroupMember target = groupMemberRepository.findByGroupIdAndAccountId(groupId, memberId)
                .orElseThrow(() -> new BadRequestException("Người này không phải thành viên nhóm"));
        if (target.getRole() == GroupMemberRole.OWNER) {
            throw new BadRequestException("Không thể xoá chủ nhóm khỏi nhóm");
        }

        groupMemberRepository.delete(target);
        return new GroupDtos.RemoveMemberResponse(groupId, memberId);
    }

    /** Xoá nhóm là hành động huỷ diệt nên yêu cầu đúng OWNER — ADMIN của nhóm không đủ */
    @Transactional
    public GroupDtos.DeleteGroupResponse delete(Integer groupId, Integer callerId) {
        if (groupMemberRepository.countByGroupIdAndAccountIdAndRole(groupId, callerId,
                GroupMemberRole.OWNER) == 0) {
            throw new ForbiddenException("Chỉ chủ nhóm mới được xoá nhóm");
        }
        Group group = groupRepository.findById(groupId).orElseThrow(BadRequestException::new);
        groupRepository.delete(group);
        return new GroupDtos.DeleteGroupResponse(groupId);
    }

    @Transactional
    public GroupDtos.ModifyGroupResponse modify(Integer groupId, String name, String type, String bio,
                                                 String coverUrl, Integer callerId) {
        requireManager(groupId, callerId);

        Group group = groupRepository.findById(groupId).orElseThrow(BadRequestException::new);
        // Trường không gửi lên thì giữ nguyên giá trị cũ
        if (name != null) {
            group.setName(name);
        }
        if (type != null) {
            group.setType("PUBLIC".equals(type) ? GroupType.PUBLIC : GroupType.PRIVATE);
        }
        if (bio != null) {
            group.setBio(bio);
        }
        if (coverUrl != null) {
            group.setCoverUrl(coverUrl);
        }

        return new GroupDtos.ModifyGroupResponse(groupRepository.save(group).getId(), true);
    }

    @Transactional(readOnly = true)
    public GroupDtos.GroupDetail getById(Integer groupId) {
        Group group = groupRepository.findDetailById(groupId).orElseThrow(BadRequestException::new);
        return GroupMapper.toDetail(group);
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.GroupSummary> getListOwned(Integer accountId) {
        List<Integer> ids = groupMemberRepository.findByAccountIdAndRole(accountId, GroupMemberRole.OWNER)
                .stream().map(GroupMember::getGroupId).toList();
        return summaries(ids);
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.GroupSummary> getListJoined(Integer accountId) {
        List<Integer> ids = groupMemberRepository.findByAccountId(accountId)
                .stream().map(GroupMember::getGroupId).toList();
        return summaries(ids);
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.GroupSummary> getListSuggest() {
        return summaries(groupRepository.findSuggestIds());
    }

    private List<GroupDtos.GroupSummary> summaries(List<Integer> groupIds) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return groupRepository.findAllDetailByIds(groupIds).stream()
                .sorted(Comparator.comparing(Group::getId))
                .map(GroupMapper::toSummary)
                .toList();
    }

    private void requireManager(Integer groupId, Integer accountId) {
        if (groupMemberRepository.countManagers(groupId, accountId) == 0) {
            throw new ForbiddenException("Bạn không có quyền quản trị nhóm này");
        }
    }

    /** Dùng bởi postPolicy: group PRIVATE chỉ thành viên mới đăng bài được */
    @Transactional(readOnly = true)
    public boolean isMember(Integer groupId, Integer accountId) {
        return groupMemberRepository.findByGroupIdAndAccountId(groupId, accountId).isPresent();
    }

    static final class GroupMapper {

        static GroupDtos.GroupDetail toDetail(Group group) {
            return new GroupDtos.GroupDetail(group.getCreatedAt(), group.getUpdatedAt(), group.getDeletedAt(),
                    group.getId(), group.getName(), group.getType(), group.getBio(), group.getCoverUrl(),
                    group.getMembers().stream()
                            .sorted(Comparator.comparing(GroupMember::getAccountId))
                            .map(GroupMapper::toMemberView)
                            .toList());
        }

        static GroupDtos.GroupMemberView toMemberView(GroupMember member) {
            return new GroupDtos.GroupMemberView(member.getCreatedAt(), member.getUpdatedAt(),
                    member.getDeletedAt(), member.getGroupId(), member.getAccountId(),
                    AccountMapper.toBrief(member.getAccount()), member.getRole(), member.getJoinedAt());
        }

        static GroupDtos.GroupSummary toSummary(Group group) {
            return new GroupDtos.GroupSummary(group.getId(), group.getName(), ownerIdOf(group),
                    group.getBio(), group.getCoverUrl(), group.getType(), group.getMembers().size(),
                    group.getCreatedAt());
        }

        /** Chủ nhóm là bản ghi group_members có role = OWNER; không có thì trả 0 như bản TS */
        private static Integer ownerIdOf(Group group) {
            return group.getMembers().stream()
                    .filter(member -> member.getRole() == GroupMemberRole.OWNER)
                    .map(GroupMember::getAccountId)
                    .findFirst()
                    .orElse(0);
        }

        private GroupMapper() {
        }
    }
}

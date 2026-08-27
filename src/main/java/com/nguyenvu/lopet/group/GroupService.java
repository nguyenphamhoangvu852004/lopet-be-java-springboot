package com.nguyenvu.lopet.group;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ConflictException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.group.dto.GroupDtos;
import com.nguyenvu.lopet.group.entity.Group;
import com.nguyenvu.lopet.group.entity.GroupMember;
import com.nguyenvu.lopet.group.entity.GroupMemberRole;
import com.nguyenvu.lopet.group.entity.GroupMemberStatus;
import com.nguyenvu.lopet.group.entity.GroupType;
import com.nguyenvu.lopet.group.repository.GroupMemberRepository;
import com.nguyenvu.lopet.group.repository.GroupRepository;
import com.nguyenvu.lopet.notification.NotificationPublisher;
import com.nguyenvu.lopet.security.CurrentUser;

import lombok.RequiredArgsConstructor;

/**
 * Quyền quản trị nhóm nằm ở {@code group_members.role}, không phải ở role toàn cục — đó là lý do
 * mọi kiểm tra ở đây đi qua {@link #requireManager} chứ không qua permission.
 *
 * <p>Ba mức khác nhau và không được lẫn: {@link #requireActiveMember} (bất kỳ thành viên) cho việc
 * MỜI người khác, {@link #requireManager} (OWNER hoặc ADMIN) cho thao tác quản trị thường ngày kể cả
 * duyệt yêu cầu vào nhóm, và đúng OWNER cho hành động huỷ diệt là xoá nhóm.
 *
 * <p><b>Chủ thể của mọi thao tác là TÀI KHOẢN</b>, lấy từ {@code CurrentUser}: khoá chính
 * của {@code group_members} là {@code (group_id, account_id)}, nên "tài khoản của tôi có quyền quản trị
 * nhóm này" không còn là câu hỏi trả lời được. Hệ quả cố ý: một người có hai thú cưng, đưa con A
 * làm chủ nhóm, thì khi đang thao tác nhân danh con B họ KHÔNG quản trị được nhóm đó.
 *
 * <p>Ngoại lệ là hai route liệt kê theo tài khoản ({@code /owned/:id}, {@code /joined/:id}) — chúng
 * trả về nhóm mà BẤT KỲ thú cưng nào của tài khoản tham gia, vì đó là câu hỏi mà giao diện "nhóm
 * của tôi" thật sự đang hỏi.
 *
 * <h2>Hai cửa vào nhóm, hai người duyệt khác nhau</h2>
 *
 * <pre>
 *   Tự xin vào  nhóm PUBLIC  -&gt; ACTIVE ngay, không ai duyệt
 *   Tự xin vào  nhóm PRIVATE -&gt; PENDING (invited_by NULL), quản trị nhóm duyệt
 *   Được MỜI    cả hai loại  -&gt; PENDING (invited_by = người mời), CHÍNH NGƯỜI ĐƯỢC MỜI duyệt
 * </pre>
 *
 * <p>Không có đường nào đưa một người vào nhóm mà thiếu hành động của chính họ hoặc của quản trị
 * nhóm. Trước bản này {@code POST /v1/groups/invites} là một lệnh INSERT thẳng của quản trị: người
 * bị "mời" thành thành viên ngay, không được hỏi. Rời nhóm và bị từ chối đều là XOÁ hàng, nên một
 * người bị từ chối vẫn xin lại được.
 *
 * <p><b>Mời không còn đòi quyền quản trị.</b> Bất kỳ thành viên ACTIVE nào cũng mời được — an toàn
 * vì lời mời chỉ là một hàng PENDING, không cấp quyền đọc gì cho tới khi người được mời đồng ý.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AccountRepository accountRepository;
    private final NotificationPublisher notificationPublisher;

    @Transactional
    public GroupDtos.CreateGroupResponse create(String name, String type, String bio, String coverUrl) {
        Account owner = requireActingAccount();

        Group group = groupRepository.save(Group.builder()
                .name(name)
                // Bất kỳ giá trị nào khác chuỗi PUBLIC đều thành PRIVATE — giữ nguyên phép so
                // sánh của controller TS, kể cả khi client gửi thiếu hoặc gửi rác.
                .type("PUBLIC".equals(type) ? GroupType.PUBLIC : GroupType.PRIVATE)
                .bio(bio == null ? "" : bio)
                .coverUrl(coverUrl == null ? "" : coverUrl)
                .build());

        // Người tạo trở thành OWNER trong group_members thay cho cột groups.owner cũ
        groupMemberRepository.save(GroupMember.builder()
                .groupId(group.getId())
                .accountId(owner.getId())
                .role(GroupMemberRole.OWNER)
                .status(GroupMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build());

        return new GroupDtos.CreateGroupResponse(group.getId(), group.getName(), group.getType(),
                owner.getId(), group.getBio(), group.getCoverUrl(), group.getCreatedAt());
    }

    /**
     * Tự tham gia nhóm. Nhóm PUBLIC vào được ngay; nhóm PRIVATE tạo yêu cầu chờ quản trị duyệt.
     *
     * <p>Trường hợp đang có lời mời chưa trả lời thì bấm "tham gia" được coi là CHẤP NHẬN lời mời
     * đó, không phải lỗi: kết quả người dùng muốn là như nhau, và bắt họ đi tìm hộp thư mời chỉ để
     * bấm một nút khác là vô nghĩa. Yêu cầu do chính họ gửi trước đó thì trả 409 — không có gì thêm.
     */
    @Transactional
    public GroupDtos.JoinGroupResponse join(Integer groupId) {
        Account account = requireActingAccount();
        Group group = requireGroup(groupId);

        GroupMember existing = groupMemberRepository.findByGroupIdAndAccountId(groupId, account.getId())
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == GroupMemberStatus.ACTIVE) {
                throw new ConflictException("Thú cưng đã là thành viên nhóm");
            }
            if (existing.getInvitedByAccountId() == null) {
                throw new ConflictException("Yêu cầu tham gia nhóm đang chờ được duyệt");
            }
            acceptInvite(existing, account, group);
            return new GroupDtos.JoinGroupResponse(groupId, account.getId(), GroupMemberStatus.ACTIVE);
        }

        boolean instant = group.getType() == GroupType.PUBLIC;
        GroupMember saved = groupMemberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .accountId(account.getId())
                .role(GroupMemberRole.MEMBER)
                .status(instant ? GroupMemberStatus.ACTIVE : GroupMemberStatus.PENDING)
                .joinedAt(LocalDateTime.now())
                .build());

        if (!instant) {
            notifyManagers(group, account);
        }
        return new GroupDtos.JoinGroupResponse(groupId, account.getId(), saved.getStatus());
    }

    /**
     * Rời nhóm. Chủ nhóm không rời được — cùng lý do {@link #removeMember} từ chối xoá OWNER: nhóm
     * sẽ còn lại mà không có ai đủ quyền xoá hoặc sửa nó. Chủ nhóm muốn dứt thì xoá nhóm.
     */
    @Transactional
    public GroupDtos.LeaveGroupResponse leave(Integer groupId) {
        Account account = requireActingAccount();
        GroupMember membership = groupMemberRepository
                .findActiveByGroupIdAndAccountId(groupId, account.getId())
                .orElseThrow(() -> new BadRequestException("Thú cưng này không phải thành viên nhóm"));

        if (membership.getRole() == GroupMemberRole.OWNER) {
            throw new BadRequestException("Chủ nhóm không thể rời nhóm");
        }

        groupMemberRepository.delete(membership);
        return new GroupDtos.LeaveGroupResponse(groupId, account.getId());
    }

    /** Huỷ yêu cầu do chính mình gửi. Không áp dụng cho lời mời — cái đó dùng {@link #respondToInvite} */
    @Transactional
    public GroupDtos.LeaveGroupResponse cancelJoinRequest(Integer groupId) {
        Account account = requireActingAccount();
        GroupMember pending = groupMemberRepository
                .findPendingByGroupIdAndAccountId(groupId, account.getId())
                .filter(member -> member.getInvitedByAccountId() == null)
                .orElseThrow(() -> new NotFoundException("Không có yêu cầu tham gia nhóm nào đang chờ"));

        groupMemberRepository.delete(pending);
        return new GroupDtos.LeaveGroupResponse(groupId, account.getId());
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.PendingMemberView> listJoinRequests(Integer groupId) {
        requireManager(groupId, requireActingAccount().getId());
        return groupMemberRepository.findPendingRequests(groupId).stream()
                .map(member -> new GroupDtos.PendingMemberView(member.getGroupId(), member.getAccountId(),
                        GroupMapper.toMemberAccount(member.getAccount()), member.getCreatedAt()))
                .toList();
    }

    /**
     * Duyệt hoặc từ chối một yêu cầu vào nhóm. Chỉ chạm được hàng PENDING do người dùng TỰ gửi
     * ({@code invited_by} null) — một lời mời đang chờ không phải việc của quản trị nhóm, người duyệt
     * nó là chính người được mời.
     */
    @Transactional
    public GroupDtos.JoinGroupResponse reviewJoinRequest(Integer groupId, Integer accountId, boolean approve) {
        Account reviewer = requireActingAccount();
        requireManager(groupId, reviewer.getId());

        GroupMember pending = groupMemberRepository.findPendingByGroupIdAndAccountId(groupId, accountId)
                .filter(member -> member.getInvitedByAccountId() == null)
                .orElseThrow(() -> new NotFoundException("Không có yêu cầu tham gia nhóm nào đang chờ"));

        if (!approve) {
            groupMemberRepository.delete(pending);
            return new GroupDtos.JoinGroupResponse(groupId, accountId, GroupMemberStatus.PENDING);
        }

        Account requester = pending.getAccount();
        activate(pending);
        notificationPublisher.groupJoinApproved(accountIdOf(reviewer), accountIdOf(requester), groupId);
        return new GroupDtos.JoinGroupResponse(groupId, accountId, GroupMemberStatus.ACTIVE);
    }

    /**
     * Mời một thú cưng khác vào nhóm. Chỉ tạo hàng PENDING — người được mời phải tự chấp nhận, xem
     * {@link #respondToInvite}. Người mời chỉ cần là thành viên ACTIVE, không cần quyền quản trị.
     */
    @Transactional
    public GroupDtos.InviteResponse invite(Integer groupId, Integer inviteeAccountId) {
        Account inviter = requireActingAccount();
        Group group = requireGroup(groupId);
        requireActiveMember(groupId, inviter.getId());

        Account invitee = accountRepository.findById(inviteeAccountId)
                .orElseThrow(() -> new BadRequestException(
                        "Thú cưng không tồn tại hoặc đã ngừng hoạt động"));
        if (invitee.getId().equals(inviter.getId())) {
            throw new BadRequestException("Không thể tự mời chính mình");
        }

        GroupMember existing = groupMemberRepository.findByGroupIdAndAccountId(groupId, invitee.getId())
                .orElse(null);
        if (existing != null) {
            throw new ConflictException(existing.getStatus() == GroupMemberStatus.ACTIVE
                    ? "Thú cưng đã ở trong nhóm"
                    : "Thú cưng này đã có yêu cầu hoặc lời mời đang chờ");
        }

        groupMemberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .accountId(invitee.getId())
                .role(GroupMemberRole.MEMBER)
                .status(GroupMemberStatus.PENDING)
                .invitedByAccountId(inviter.getId())
                .joinedAt(LocalDateTime.now())
                .build());

        notificationPublisher.groupInvited(accountIdOf(inviter), accountIdOf(invitee), group.getId());
        return new GroupDtos.InviteResponse(groupId, invitee.getId(), GroupMemberStatus.PENDING);
    }

    /** Chấp nhận hoặc từ chối một lời mời. Chỉ chạm được hàng có {@code invited_by} khác null. */
    @Transactional
    public GroupDtos.JoinGroupResponse respondToInvite(Integer groupId, boolean accept) {
        Account account = requireActingAccount();
        Group group = requireGroup(groupId);

        GroupMember invite = groupMemberRepository.findPendingByGroupIdAndAccountId(groupId, account.getId())
                .filter(member -> member.getInvitedByAccountId() != null)
                .orElseThrow(() -> new NotFoundException("Không có lời mời nào đang chờ"));

        if (!accept) {
            groupMemberRepository.delete(invite);
            return new GroupDtos.JoinGroupResponse(groupId, account.getId(), GroupMemberStatus.PENDING);
        }

        acceptInvite(invite, account, group);
        return new GroupDtos.JoinGroupResponse(groupId, account.getId(), GroupMemberStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.PendingInviteView> listMyInvites() {
        Account account = requireActingAccount();
        return groupMemberRepository.findPendingInvitesForAccount(account.getId()).stream()
                .map(invite -> {
                    Group group = invite.getGroup();
                    // Người mời có thể đã ngừng hoạt động: cột invited_by cố ý không có khoá ngoại,
                    // nên id ở đây vẫn khác null dù tài khoản đằng sau không còn nạp được.
                    Account invitedBy = accountRepository.findById(invite.getInvitedByAccountId()).orElse(null);
                    return new GroupDtos.PendingInviteView(invite.getGroupId(), group.getName(),
                            group.getType(), invite.getAccountId(), GroupMapper.toMemberAccount(invitedBy),
                            invite.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public GroupDtos.RemoveMemberResponse removeMember(Integer groupId, Integer memberAccountId) {
        requireManager(groupId, requireActingAccount().getId());

        GroupMember target = groupMemberRepository.findActiveByGroupIdAndAccountId(groupId, memberAccountId)
                .orElseThrow(() -> new BadRequestException("Thú cưng này không phải thành viên nhóm"));
        if (target.getRole() == GroupMemberRole.OWNER) {
            throw new BadRequestException("Không thể xoá chủ nhóm khỏi nhóm");
        }

        groupMemberRepository.delete(target);
        return new GroupDtos.RemoveMemberResponse(groupId, memberAccountId);
    }

    /** Xoá nhóm là hành động huỷ diệt nên yêu cầu đúng OWNER — ADMIN của nhóm không đủ */
    @Transactional
    public GroupDtos.DeleteGroupResponse delete(Integer groupId) {
        Integer callerAccountId = requireActingAccount().getId();
        if (groupMemberRepository.countActiveByGroupIdAndAccountIdAndRole(groupId, callerAccountId,
                GroupMemberRole.OWNER) == 0) {
            throw new ForbiddenException("Chỉ chủ nhóm mới được xoá nhóm");
        }
        Group group = groupRepository.findById(groupId).orElseThrow(BadRequestException::new);
        groupRepository.delete(group);
        return new GroupDtos.DeleteGroupResponse(groupId);
    }

    @Transactional
    public GroupDtos.ModifyGroupResponse modify(Integer groupId, String name, String type, String bio,
                                                 String coverUrl) {
        requireManager(groupId, requireActingAccount().getId());

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

    /**
     * Chi tiết nhóm, đã lọc theo quyền xem của người đang xem ({@code null} = khách chưa đăng nhập).
     *
     * <p>Nhóm PRIVATE mà người xem không phải thành viên ACTIVE thì DANH SÁCH THÀNH VIÊN bị rút
     * rỗng: route này không xác thực bắt buộc, nên trước đây handle và ảnh của từng thú cưng trong
     * mọi nhóm kín đều đọc được chỉ bằng cách đoán id nhóm. Metadata (tên, bio, ảnh bìa, số thành
     * viên) vẫn trả về — chọn như vậy chứ không trả 404 để nhóm kín còn TÌM được mà xin vào, nếu che
     * hẳn thì không có đường nào gửi yêu cầu.
     */
    @Transactional(readOnly = true)
    public GroupDtos.GroupDetail getById(Integer groupId, Integer viewerAccountId) {
        Group group = groupRepository.findDetailById(groupId).orElseThrow(BadRequestException::new);
        return GroupMapper.toDetail(group, viewerStatusOf(group, viewerAccountId));
    }

    /** Nhóm do BẤT KỲ thú cưng nào của tài khoản làm chủ */
    @Transactional(readOnly = true)
    public List<GroupDtos.GroupSummary> getListOwned(Integer accountId) {
        List<Integer> ids = groupMemberRepository
                .findActiveByAccountIdAndRole(accountId, GroupMemberRole.OWNER)
                .stream().map(GroupMember::getGroupId).toList();
        return summaries(ids);
    }

    /** Nhóm mà tài khoản đang là thành viên ACTIVE */
    @Transactional(readOnly = true)
    public List<GroupDtos.GroupSummary> getListJoined(Integer accountId) {
        List<Integer> ids = groupMemberRepository.findActiveByAccountId(accountId)
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

    private Group requireGroup(Integer groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhóm"));
    }

    private void requireManager(Integer groupId, Integer accountId) {
        if (groupMemberRepository.countManagers(groupId, accountId) == 0) {
            throw new ForbiddenException("Thú cưng này không có quyền quản trị nhóm");
        }
    }

    private void requireActiveMember(Integer groupId, Integer accountId) {
        if (groupMemberRepository.findActiveByGroupIdAndAccountId(groupId, accountId).isEmpty()) {
            throw new ForbiddenException("Chỉ thành viên của nhóm mới được mời người khác");
        }
    }

    /**
     * Hàng PENDING thành thành viên thật. Đóng dấu lại {@code joinedAt}: đó mới là lúc tư cách thành
     * viên bắt đầu, còn lúc gửi yêu cầu đã nằm ở {@code createdAt}.
     */
    private void activate(GroupMember member) {
        member.setStatus(GroupMemberStatus.ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        groupMemberRepository.save(member);
    }

    private void acceptInvite(GroupMember invite, Account invitee, Group group) {
        Integer inviterAccountId = invite.getInvitedByAccountId();
        activate(invite);
        accountRepository.findById(inviterAccountId).ifPresent(inviter -> notificationPublisher
                .groupInviteAccepted(accountIdOf(invitee), accountIdOf(inviter), group.getId()));
    }

    /**
     * Một thông báo cho MỖI quản trị nhóm: bảng notifications là quan hệ một-người-nhận, không có
     * khái niệm gửi cho một tập người.
     */
    private void notifyManagers(Group group, Account requester) {
        Integer actorId = accountIdOf(requester);
        accountRepository.findAllById(groupMemberRepository.findActiveManagerAccountIds(group.getId()))
                .forEach(manager -> notificationPublisher.groupJoinRequested(actorId,
                        accountIdOf(manager), group.getId()));
    }

    private GroupDtos.ViewerStatus viewerStatusOf(Group group, Integer viewerAccountId) {
        if (viewerAccountId == null) {
            return GroupDtos.ViewerStatus.NONE;
        }
        return groupMemberRepository.findByGroupIdAndAccountId(group.getId(), viewerAccountId)
                .map(member -> {
                    if (member.getStatus() == GroupMemberStatus.ACTIVE) {
                        return GroupDtos.ViewerStatus.MEMBER;
                    }
                    return member.getInvitedByAccountId() == null
                            ? GroupDtos.ViewerStatus.PENDING_REQUEST
                            : GroupDtos.ViewerStatus.PENDING_INVITE;
                })
                .orElse(GroupDtos.ViewerStatus.NONE);
    }

    /** Dùng bởi postPolicy: chỉ thành viên ACTIVE mới được đăng bài, hàng PENDING không tính */
    @Transactional(readOnly = true)
    public boolean isMember(Integer groupId, Integer accountId) {
        return groupMemberRepository.findActiveByGroupIdAndAccountId(groupId, accountId).isPresent();
    }

    /**
     * Trả null khi tài khoản không nạp được — {@code publish} tự bỏ qua.
     */
    private Integer accountIdOf(Account account) {
        return account == null ? null : account.getId();
    }

    /** Tài khoản đang thao tác, lấy từ token — không bao giờ từ body hay path param */
    private Account requireActingAccount() {
        Integer accountId = CurrentUser.require().id();
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException("Tài khoản không tồn tại"));
    }

    static final class GroupMapper {

        /**
         * Chỉ hàng ACTIVE được coi là thành viên. Lọc ở đây thay vì trong mệnh đề
         * {@code left join fetch} của repository: một điều kiện đặt sai chỗ trong fetch join sẽ cắt
         * luôn cả nhóm khỏi kết quả (xem ghi chú ở {@code GroupRepository}), còn lọc tại mapper thì
         * cả {@code toDetail} và {@code toSummary} dùng chung một câu trả lời.
         */
        private static List<GroupMember> activeMembersOf(Group group) {
            return group.getMembers().stream()
                    .filter(member -> member.getStatus() == GroupMemberStatus.ACTIVE)
                    .sorted(Comparator.comparing(GroupMember::getAccountId))
                    .toList();
        }

        static GroupDtos.GroupDetail toDetail(Group group, GroupDtos.ViewerStatus viewerStatus) {
            List<GroupMember> active = activeMembersOf(group);
            boolean restricted = group.getType() == GroupType.PRIVATE
                    && viewerStatus != GroupDtos.ViewerStatus.MEMBER;

            return new GroupDtos.GroupDetail(group.getCreatedAt(), group.getUpdatedAt(),
                    group.getDeletedAt(), group.getId(), group.getName(), group.getType(),
                    group.getBio(), group.getCoverUrl(),
                    restricted ? List.of() : active.stream().map(GroupMapper::toMemberView).toList(),
                    active.size(), restricted, viewerStatus);
        }

        static GroupDtos.GroupMemberView toMemberView(GroupMember member) {
            return new GroupDtos.GroupMemberView(member.getCreatedAt(), member.getUpdatedAt(),
                    member.getDeletedAt(), member.getGroupId(), member.getAccountId(),
                    toMemberAccount(member.getAccount()), member.getRole(), member.getJoinedAt());
        }

        /**
         * {@code account} có thể null khi tài khoản đã bị xoá mềm: hàng {@code group_members} vẫn còn
         * nhưng {@code @SQLRestriction} trên {@code Account} loại nó khỏi kết quả nạp. Trả object rỗng
         * thay vì null để client không phải xử lý hai hình dạng cho cùng một khoá.
         */
        static GroupDtos.GroupMemberAccount toMemberAccount(Account account) {
            if (account == null) {
                return new GroupDtos.GroupMemberAccount(null, "", "", "");
            }
            AccountProfile profile = account.getAccountProfile();
            return new GroupDtos.GroupMemberAccount(account.getId(), orEmpty(account.getUsername()),
                    profile == null ? "" : orEmpty(profile.getFullName()),
                    profile == null ? "" : orEmpty(profile.getAvatarUrl()));
        }

        static GroupDtos.GroupSummary toSummary(Group group) {
            List<GroupMember> active = activeMembersOf(group);
            return new GroupDtos.GroupSummary(group.getId(), group.getName(), ownerAccountIdOf(active),
                    group.getBio(), group.getCoverUrl(), group.getType(), active.size(),
                    group.getCreatedAt());
        }

        /** Chủ nhóm là bản ghi group_members có role = OWNER; không có thì trả 0 như bản TS */
        private static Integer ownerAccountIdOf(List<GroupMember> activeMembers) {
            return activeMembers.stream()
                    .filter(member -> member.getRole() == GroupMemberRole.OWNER)
                    .map(GroupMember::getAccountId)
                    .findFirst()
                    .orElse(0);
        }

        private static String orEmpty(String value) {
            return value == null ? "" : value;
        }

        private GroupMapper() {
        }
    }
}

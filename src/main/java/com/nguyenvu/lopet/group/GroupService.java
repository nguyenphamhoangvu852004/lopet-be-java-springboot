package com.nguyenvu.lopet.group;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.pet.repository.PetRepository;
import com.nguyenvu.lopet.petprofile.entity.PetProfile;
import com.nguyenvu.lopet.security.petcontext.PetContext;

import lombok.RequiredArgsConstructor;

/**
 * Quyền quản trị nhóm nằm ở {@code group_members.role}, không phải ở role toàn cục — đó là lý do
 * mọi kiểm tra ở đây đi qua {@link #requireManager} chứ không qua permission.
 *
 * <p>Ba mức khác nhau và không được lẫn: {@link #requireActiveMember} (bất kỳ thành viên) cho việc
 * MỜI người khác, {@link #requireManager} (OWNER hoặc ADMIN) cho thao tác quản trị thường ngày kể cả
 * duyệt yêu cầu vào nhóm, và đúng OWNER cho hành động huỷ diệt là xoá nhóm.
 *
 * <p><b>Chủ thể của mọi thao tác là THÚ CƯNG</b>, lấy từ {@link PetContext#require()}: khoá chính
 * của {@code group_members} là {@code (group_id, pet_id)}, nên "tài khoản của tôi có quyền quản trị
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
 *   Được MỜI    cả hai loại  -&gt; PENDING (invited_by = người mời), CHÍNH PET ĐƯỢC MỜI duyệt
 * </pre>
 *
 * <p>Không có đường nào đưa một pet vào nhóm mà thiếu hành động của chính pet đó hoặc của quản trị
 * nhóm. Trước bản này {@code POST /v1/groups/invites} là một lệnh INSERT thẳng của quản trị: người
 * bị "mời" thành thành viên ngay, không được hỏi. Rời nhóm và bị từ chối đều là XOÁ hàng, nên một
 * pet bị từ chối vẫn xin lại được.
 *
 * <p><b>Mời không còn đòi quyền quản trị.</b> Bất kỳ thành viên ACTIVE nào cũng mời được — an toàn
 * vì lời mời chỉ là một hàng PENDING, không cấp quyền đọc gì cho tới khi người được mời đồng ý.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PetRepository petRepository;
    private final NotificationPublisher notificationPublisher;

    @Transactional
    public GroupDtos.CreateGroupResponse create(String name, String type, String bio, String coverUrl) {
        Pet owner = requireActingPet();

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
                .petId(owner.getId())
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
     * đó, không phải lỗi: kết quả pet muốn là như nhau, và bắt họ đi tìm hộp thư mời chỉ để bấm một
     * nút khác là vô nghĩa. Yêu cầu do chính pet gửi trước đó thì trả 409 — không có gì để làm thêm.
     */
    @Transactional
    public GroupDtos.JoinGroupResponse join(Integer groupId) {
        Pet pet = requireActingPet();
        Group group = requireGroup(groupId);

        GroupMember existing = groupMemberRepository.findByGroupIdAndPetId(groupId, pet.getId())
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == GroupMemberStatus.ACTIVE) {
                throw new ConflictException("Thú cưng đã là thành viên nhóm");
            }
            if (existing.getInvitedByPetId() == null) {
                throw new ConflictException("Yêu cầu tham gia nhóm đang chờ được duyệt");
            }
            acceptInvite(existing, pet, group);
            return new GroupDtos.JoinGroupResponse(groupId, pet.getId(), GroupMemberStatus.ACTIVE);
        }

        boolean instant = group.getType() == GroupType.PUBLIC;
        GroupMember saved = groupMemberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .petId(pet.getId())
                .role(GroupMemberRole.MEMBER)
                .status(instant ? GroupMemberStatus.ACTIVE : GroupMemberStatus.PENDING)
                .joinedAt(LocalDateTime.now())
                .build());

        if (!instant) {
            notifyManagers(group, pet);
        }
        return new GroupDtos.JoinGroupResponse(groupId, pet.getId(), saved.getStatus());
    }

    /**
     * Rời nhóm. Chủ nhóm không rời được — cùng lý do {@link #removeMember} từ chối xoá OWNER: nhóm
     * sẽ còn lại mà không có ai đủ quyền xoá hoặc sửa nó. Chủ nhóm muốn dứt thì xoá nhóm.
     */
    @Transactional
    public GroupDtos.LeaveGroupResponse leave(Integer groupId) {
        Pet pet = requireActingPet();
        GroupMember membership = groupMemberRepository
                .findActiveByGroupIdAndPetId(groupId, pet.getId())
                .orElseThrow(() -> new BadRequestException("Thú cưng này không phải thành viên nhóm"));

        if (membership.getRole() == GroupMemberRole.OWNER) {
            throw new BadRequestException("Chủ nhóm không thể rời nhóm");
        }

        groupMemberRepository.delete(membership);
        return new GroupDtos.LeaveGroupResponse(groupId, pet.getId());
    }

    /** Huỷ yêu cầu do chính pet gửi. Không áp dụng cho lời mời — cái đó dùng {@link #respondToInvite} */
    @Transactional
    public GroupDtos.LeaveGroupResponse cancelJoinRequest(Integer groupId) {
        Pet pet = requireActingPet();
        GroupMember pending = groupMemberRepository
                .findPendingByGroupIdAndPetId(groupId, pet.getId())
                .filter(member -> member.getInvitedByPetId() == null)
                .orElseThrow(() -> new NotFoundException("Không có yêu cầu tham gia nhóm nào đang chờ"));

        groupMemberRepository.delete(pending);
        return new GroupDtos.LeaveGroupResponse(groupId, pet.getId());
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.PendingMemberView> listJoinRequests(Integer groupId) {
        requireManager(groupId, requireActingPet().getId());
        return groupMemberRepository.findPendingRequests(groupId).stream()
                .map(member -> new GroupDtos.PendingMemberView(member.getGroupId(), member.getPetId(),
                        GroupMapper.toMemberPet(member.getPet()), member.getCreatedAt()))
                .toList();
    }

    /**
     * Duyệt hoặc từ chối một yêu cầu vào nhóm. Chỉ chạm được hàng PENDING do pet TỰ gửi
     * ({@code invited_by} null) — một lời mời đang chờ không phải việc của quản trị nhóm, người duyệt
     * nó là chính pet được mời.
     */
    @Transactional
    public GroupDtos.JoinGroupResponse reviewJoinRequest(Integer groupId, Integer petId, boolean approve) {
        Pet reviewer = requireActingPet();
        requireManager(groupId, reviewer.getId());

        GroupMember pending = groupMemberRepository.findPendingByGroupIdAndPetId(groupId, petId)
                .filter(member -> member.getInvitedByPetId() == null)
                .orElseThrow(() -> new NotFoundException("Không có yêu cầu tham gia nhóm nào đang chờ"));

        if (!approve) {
            groupMemberRepository.delete(pending);
            return new GroupDtos.JoinGroupResponse(groupId, petId, GroupMemberStatus.PENDING);
        }

        Pet requester = pending.getPet();
        activate(pending);
        notificationPublisher.groupJoinApproved(accountIdOf(reviewer), accountIdOf(requester), groupId);
        return new GroupDtos.JoinGroupResponse(groupId, petId, GroupMemberStatus.ACTIVE);
    }

    /**
     * Mời một thú cưng khác vào nhóm. Chỉ tạo hàng PENDING — người được mời phải tự chấp nhận, xem
     * {@link #respondToInvite}. Người mời chỉ cần là thành viên ACTIVE, không cần quyền quản trị.
     */
    @Transactional
    public GroupDtos.InviteResponse invite(Integer groupId, Integer inviteePetId) {
        Pet inviter = requireActingPet();
        Group group = requireGroup(groupId);
        requireActiveMember(groupId, inviter.getId());

        Pet invitee = petRepository.findById(inviteePetId)
                .orElseThrow(() -> new BadRequestException(
                        "Thú cưng không tồn tại hoặc đã ngừng hoạt động"));
        if (invitee.getId().equals(inviter.getId())) {
            throw new BadRequestException("Không thể tự mời chính mình");
        }

        GroupMember existing = groupMemberRepository.findByGroupIdAndPetId(groupId, invitee.getId())
                .orElse(null);
        if (existing != null) {
            throw new ConflictException(existing.getStatus() == GroupMemberStatus.ACTIVE
                    ? "Thú cưng đã ở trong nhóm"
                    : "Thú cưng này đã có yêu cầu hoặc lời mời đang chờ");
        }

        groupMemberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .petId(invitee.getId())
                .role(GroupMemberRole.MEMBER)
                .status(GroupMemberStatus.PENDING)
                .invitedByPetId(inviter.getId())
                .joinedAt(LocalDateTime.now())
                .build());

        notificationPublisher.groupInvited(accountIdOf(inviter), accountIdOf(invitee), group.getId());
        return new GroupDtos.InviteResponse(groupId, invitee.getId(), GroupMemberStatus.PENDING);
    }

    /** Chấp nhận hoặc từ chối một lời mời. Chỉ chạm được hàng có {@code invited_by} khác null. */
    @Transactional
    public GroupDtos.JoinGroupResponse respondToInvite(Integer groupId, boolean accept) {
        Pet pet = requireActingPet();
        Group group = requireGroup(groupId);

        GroupMember invite = groupMemberRepository.findPendingByGroupIdAndPetId(groupId, pet.getId())
                .filter(member -> member.getInvitedByPetId() != null)
                .orElseThrow(() -> new NotFoundException("Không có lời mời nào đang chờ"));

        if (!accept) {
            groupMemberRepository.delete(invite);
            return new GroupDtos.JoinGroupResponse(groupId, pet.getId(), GroupMemberStatus.PENDING);
        }

        acceptInvite(invite, pet, group);
        return new GroupDtos.JoinGroupResponse(groupId, pet.getId(), GroupMemberStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.PendingInviteView> listMyInvites() {
        Pet pet = requireActingPet();
        return groupMemberRepository.findPendingInvitesForPet(pet.getId()).stream()
                .map(invite -> {
                    Group group = invite.getGroup();
                    // Người mời có thể đã ngừng hoạt động: cột invited_by cố ý không có khoá ngoại,
                    // nên id ở đây vẫn khác null dù pet đằng sau không còn nạp được.
                    Pet invitedBy = petRepository.findById(invite.getInvitedByPetId()).orElse(null);
                    return new GroupDtos.PendingInviteView(invite.getGroupId(), group.getName(),
                            group.getType(), invite.getPetId(), GroupMapper.toMemberPet(invitedBy),
                            invite.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public GroupDtos.RemoveMemberResponse removeMember(Integer groupId, Integer memberPetId) {
        requireManager(groupId, requireActingPet().getId());

        GroupMember target = groupMemberRepository.findActiveByGroupIdAndPetId(groupId, memberPetId)
                .orElseThrow(() -> new BadRequestException("Thú cưng này không phải thành viên nhóm"));
        if (target.getRole() == GroupMemberRole.OWNER) {
            throw new BadRequestException("Không thể xoá chủ nhóm khỏi nhóm");
        }

        groupMemberRepository.delete(target);
        return new GroupDtos.RemoveMemberResponse(groupId, memberPetId);
    }

    /** Xoá nhóm là hành động huỷ diệt nên yêu cầu đúng OWNER — ADMIN của nhóm không đủ */
    @Transactional
    public GroupDtos.DeleteGroupResponse delete(Integer groupId) {
        Integer callerPetId = requireActingPet().getId();
        if (groupMemberRepository.countActiveByGroupIdAndPetIdAndRole(groupId, callerPetId,
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
        requireManager(groupId, requireActingPet().getId());

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
     * Chi tiết nhóm, đã lọc theo quyền xem của PET đang xem ({@code null} = khách hoặc chưa chọn
     * pet).
     *
     * <p>Nhóm PRIVATE mà người xem không phải thành viên ACTIVE thì DANH SÁCH THÀNH VIÊN bị rút
     * rỗng: route này không xác thực bắt buộc, nên trước đây handle và ảnh của từng thú cưng trong
     * mọi nhóm kín đều đọc được chỉ bằng cách đoán id nhóm. Metadata (tên, bio, ảnh bìa, số thành
     * viên) vẫn trả về — chọn như vậy chứ không trả 404 để nhóm kín còn TÌM được mà xin vào, nếu che
     * hẳn thì không có đường nào gửi yêu cầu.
     */
    @Transactional(readOnly = true)
    public GroupDtos.GroupDetail getById(Integer groupId, Integer viewerPetId) {
        Group group = groupRepository.findDetailById(groupId).orElseThrow(BadRequestException::new);
        return GroupMapper.toDetail(group, viewerStatusOf(group, viewerPetId));
    }

    /** Nhóm do BẤT KỲ thú cưng nào của tài khoản làm chủ */
    @Transactional(readOnly = true)
    public List<GroupDtos.GroupSummary> getListOwned(Integer accountId) {
        List<Integer> ids = groupMemberRepository
                .findByOwnerAccountIdAndRole(accountId, GroupMemberRole.OWNER)
                .stream().map(GroupMember::getGroupId).toList();
        return summaries(ids);
    }

    /** Nhóm mà BẤT KỲ thú cưng nào của tài khoản đang tham gia */
    @Transactional(readOnly = true)
    public List<GroupDtos.GroupSummary> getListJoined(Integer accountId) {
        List<Integer> ids = groupMemberRepository.findByOwnerAccountId(accountId)
                .stream().map(GroupMember::getGroupId).toList();
        return summaries(ids);
    }

    /** Nhóm mà MỘT thú cưng cụ thể đang tham gia */
    @Transactional(readOnly = true)
    public List<GroupDtos.GroupSummary> getListJoinedByPet(Integer petId) {
        List<Integer> ids = groupMemberRepository.findActiveByPetId(petId)
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

    private void requireManager(Integer groupId, Integer petId) {
        if (groupMemberRepository.countManagers(groupId, petId) == 0) {
            throw new ForbiddenException("Thú cưng này không có quyền quản trị nhóm");
        }
    }

    private void requireActiveMember(Integer groupId, Integer petId) {
        if (groupMemberRepository.findActiveByGroupIdAndPetId(groupId, petId).isEmpty()) {
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

    private void acceptInvite(GroupMember invite, Pet invitee, Group group) {
        Integer inviterPetId = invite.getInvitedByPetId();
        activate(invite);
        petRepository.findById(inviterPetId).ifPresent(inviter -> notificationPublisher
                .groupInviteAccepted(accountIdOf(invitee), accountIdOf(inviter), group.getId()));
    }

    /**
     * Một thông báo cho MỖI quản trị nhóm: bảng notifications là quan hệ một-người-nhận, không có
     * khái niệm gửi cho một tập người.
     */
    private void notifyManagers(Group group, Pet requester) {
        Integer actorId = accountIdOf(requester);
        petRepository.findAllById(groupMemberRepository.findActiveManagerPetIds(group.getId()))
                .forEach(manager -> notificationPublisher.groupJoinRequested(actorId,
                        accountIdOf(manager), group.getId()));
    }

    private GroupDtos.ViewerStatus viewerStatusOf(Group group, Integer viewerPetId) {
        if (viewerPetId == null) {
            return GroupDtos.ViewerStatus.NONE;
        }
        return groupMemberRepository.findByGroupIdAndPetId(group.getId(), viewerPetId)
                .map(member -> {
                    if (member.getStatus() == GroupMemberStatus.ACTIVE) {
                        return GroupDtos.ViewerStatus.MEMBER;
                    }
                    return member.getInvitedByPetId() == null
                            ? GroupDtos.ViewerStatus.PENDING_REQUEST
                            : GroupDtos.ViewerStatus.PENDING_INVITE;
                })
                .orElse(GroupDtos.ViewerStatus.NONE);
    }

    /** Dùng bởi postPolicy: chỉ thành viên ACTIVE mới được đăng bài, hàng PENDING không tính */
    @Transactional(readOnly = true)
    public boolean isMember(Integer groupId, Integer petId) {
        return groupMemberRepository.findActiveByGroupIdAndPetId(groupId, petId).isPresent();
    }

    /**
     * Người nhận thông báo là TÀI KHOẢN, không phải pet: đồ thị thông báo ở lại phạm vi tài khoản
     * vĩnh viễn. Trả null nếu pet không còn chủ nạp được — {@code publish} tự bỏ qua.
     */
    private Integer accountIdOf(Pet pet) {
        return pet == null || pet.getAccount() == null ? null : pet.getAccount().getId();
    }

    /** Xem ghi chú cùng tên ở {@code PostService} */
    private Pet requireActingPet() {
        Integer petId = PetContext.require();
        return petRepository.findById(petId)
                .orElseThrow(() -> new BadRequestException("Thú cưng không tồn tại hoặc đã ngừng hoạt động"));
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
                    .sorted(Comparator.comparing(GroupMember::getPetId))
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
                    member.getDeletedAt(), member.getGroupId(), member.getPetId(),
                    toMemberPet(member.getPet()), member.getRole(), member.getJoinedAt());
        }

        /**
         * {@code pet} có thể null khi con vật đã ngừng hoạt động: hàng {@code group_members} vẫn còn
         * nhưng {@code @SQLRestriction} trên {@code Pet} loại nó khỏi kết quả nạp. Trả object rỗng
         * thay vì null để client không phải xử lý hai hình dạng cho cùng một khoá.
         */
        static GroupDtos.GroupMemberPet toMemberPet(Pet pet) {
            if (pet == null) {
                return new GroupDtos.GroupMemberPet(null, "", "", "", "");
            }
            PetProfile profile = pet.getPetProfile();
            return new GroupDtos.GroupMemberPet(pet.getId(), orEmpty(pet.getName()),
                    profile == null ? "" : orEmpty(profile.getHandle()),
                    profile == null ? "" : orEmpty(profile.getDisplayName()),
                    profile == null ? "" : orEmpty(profile.getAvatarUrl()));
        }

        static GroupDtos.GroupSummary toSummary(Group group) {
            List<GroupMember> active = activeMembersOf(group);
            return new GroupDtos.GroupSummary(group.getId(), group.getName(), ownerPetIdOf(active),
                    group.getBio(), group.getCoverUrl(), group.getType(), active.size(),
                    group.getCreatedAt());
        }

        /** Chủ nhóm là bản ghi group_members có role = OWNER; không có thì trả 0 như bản TS */
        private static Integer ownerPetIdOf(List<GroupMember> activeMembers) {
            return activeMembers.stream()
                    .filter(member -> member.getRole() == GroupMemberRole.OWNER)
                    .map(GroupMember::getPetId)
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

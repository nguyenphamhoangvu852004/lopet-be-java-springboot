package com.nguyenvu.lopet.group;

import static com.nguyenvu.lopet.support.AuthTestSupport.actAs;
import static com.nguyenvu.lopet.support.AuthTestSupport.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ConflictException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.group.dto.GroupDtos;
import com.nguyenvu.lopet.group.entity.Group;
import com.nguyenvu.lopet.group.entity.GroupMemberRole;
import com.nguyenvu.lopet.group.entity.GroupMemberStatus;
import com.nguyenvu.lopet.group.entity.GroupType;
import com.nguyenvu.lopet.group.repository.GroupMemberRepository;
import com.nguyenvu.lopet.group.repository.GroupRepository;
import com.nguyenvu.lopet.post.PostService;
import com.nguyenvu.lopet.post.entity.PostScope;
import com.nguyenvu.lopet.support.IntegrationTestBase;

/**
 * Cơ chế vào nhóm: tự tham gia, yêu cầu chờ duyệt, lời mời.
 *
 * <p>Chạy ở tầng SERVICE vì mọi luật ở đây nằm trong {@link GroupService} và
 * {@code PostPolicy}/{@code PostVisibility}, không nằm trong controller. Danh tính người gọi vì thế
 * phải đặt trước mỗi lời gọi bằng {@code actAs} — xem {@code AuthTestSupport}.
 *
 * <p><b>Điều được canh gắt nhất ở đây là hàng PENDING không được tính là thành viên.</b> Sau khi
 * {@code group_members} có cột {@code status}, một người ngoài TỰ tạo được hàng PENDING chỉ bằng cách
 * bấm xin vào nhóm — nên bỏ lọc {@code status = ACTIVE} ở bất kỳ truy vấn nào là mở cửa nhóm PRIVATE
 * cho bất kỳ ai. Các test {@code khong_thay_bai_khi_dang_cho} và {@code khong_dem_vao_danh_sach} tồn
 * tại để bắt đúng lỗi đó.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Tham gia nhóm — xin vào, duyệt, mời")
class GroupMembershipIntegrationTest extends IntegrationTestBase {

    /** Database riêng: xem ghi chú cùng chỗ ở PostVisibilityIntegrationTest */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> IntegrationTestBase.jdbcUrl("lopet_java_group_test"));
    }

    private static final long RUN = System.nanoTime();

    @Autowired
    private GroupService groupService;
    @Autowired
    private PostService postService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;

    /** Chủ nhóm, một quản trị viên nhóm, một thành viên thường, và hai người ngoài */
    private Integer owner;
    private Integer admin;
    private Integer member;
    private Integer outsider;
    private Integer invitee;

    @BeforeAll
    void seed() {
        inTransaction(() -> {
            owner = account("grp-owner");
            admin = account("grp-admin");
            member = account("grp-member");
            outsider = account("grp-outsider");
            invitee = account("grp-invitee");
        });
    }

    @AfterEach
    void resetAuthContext() {
        clear();
    }

    /* ---------------------------------------------------------------- helpers */

    private Integer account(String name) {
        String unique = name + "-" + RUN;
        return accountRepository.save(Account.builder()
                .email(unique + "@group.local").username(unique).password("x").isBanned(0).build()).getId();
    }

    /** Nhóm mới, {@link #owner} là OWNER và {@link #admin} được nâng thành ADMIN của nhóm */
    private Integer group(GroupType type) {
        actAs(owner);
        Integer groupId = groupService.create("grp-" + System.nanoTime(), type.name(), "", "").id();

        actAs(admin);
        groupService.join(groupId);
        if (type == GroupType.PRIVATE) {
            actAs(owner);
            groupService.reviewJoinRequest(groupId, admin, true);
        }
        inTransaction(() -> {
            var row = groupMemberRepository.findActiveByGroupIdAndAccountId(groupId, admin).orElseThrow();
            row.setRole(GroupMemberRole.ADMIN);
            groupMemberRepository.save(row);
        });
        return groupId;
    }

    /** Đưa {@link #member} thành thành viên ACTIVE của nhóm, bất kể loại nhóm */
    private void joinAsMember(Integer groupId) {
        actAs(member);
        groupService.join(groupId);
        if (statusOf(groupId, member) == GroupMemberStatus.PENDING) {
            actAs(owner);
            groupService.reviewJoinRequest(groupId, member, true);
        }
    }

    private GroupMemberStatus statusOf(Integer groupId, Integer accountId) {
        return inTransaction(() -> groupMemberRepository.findByGroupIdAndAccountId(groupId, accountId))
                .map(member -> member.getStatus()).orElse(null);
    }

    private Integer groupPost(Integer groupId, Integer authorId) {
        actAs(authorId);
        return postService.create(authorId, "bai-" + System.nanoTime(), groupId, PostScope.PUBLIC.name(),
                List.of()).postId();
    }

    private GroupDtos.GroupDetail detailAs(Integer groupId, Integer viewerId) {
        return inTransaction(() -> groupService.getById(groupId, viewerId));
    }

    /* ---------------------------------------------------------------- tests */

    @Nested
    @DisplayName("Nhóm PUBLIC: vào là vào")
    class NhomPublic {

        @Test
        @DisplayName("tự tham gia thành ACTIVE ngay, không chờ ai duyệt")
        void tu_tham_gia_active_ngay() {
            Integer groupId = group(GroupType.PUBLIC);

            actAs(outsider);
            GroupDtos.JoinGroupResponse response = groupService.join(groupId);

            assertThat(response.status()).isEqualTo(GroupMemberStatus.ACTIVE);
            assertThat(groupService.getListJoined(outsider))
                    .extracting(GroupDtos.GroupSummary::id).contains(groupId);
            assertThat(detailAs(groupId, outsider).members())
                    .extracting(GroupDtos.GroupMemberView::accountId).contains(outsider);
        }

        @Test
        @DisplayName("tham gia hai lần thì 409, không tạo hàng thứ hai")
        void tham_gia_hai_lan_thi_409() {
            Integer groupId = group(GroupType.PUBLIC);
            actAs(outsider);
            groupService.join(groupId);

            actAs(outsider);
            assertThatThrownBy(() -> groupService.join(groupId))
                    .isInstanceOf(ConflictException.class);
        }

        /**
         * ĐỌC bài của nhóm PUBLIC mở cho cả khách, nhưng ĐĂNG thì phải tham gia trước — nếu không thì
         * hành động tham gia nhóm chẳng thay đổi điều gì.
         */
        @Test
        @DisplayName("người ngoài không đăng bài được, tham gia rồi thì được")
        void muon_dang_bai_thi_phai_tham_gia() {
            Integer groupId = group(GroupType.PUBLIC);

            actAs(outsider);
            assertThatThrownBy(() -> groupPost(groupId, outsider))
                    .isInstanceOf(ForbiddenException.class);

            actAs(outsider);
            groupService.join(groupId);
            assertThat(groupPost(groupId, outsider)).isPositive();
        }

        @Test
        @DisplayName("chi tiết nhóm không bị che với người ngoài")
        void chi_tiet_khong_bi_che() {
            Integer groupId = group(GroupType.PUBLIC);
            GroupDtos.GroupDetail detail = detailAs(groupId, outsider);

            assertThat(detail.restricted()).isFalse();
            assertThat(detail.members()).isNotEmpty();
            assertThat(detail.viewerStatus()).isEqualTo(GroupDtos.ViewerStatus.NONE);
        }
    }

    @Nested
    @DisplayName("Nhóm PRIVATE: xin vào rồi chờ duyệt")
    class NhomPrivate {

        @Test
        @DisplayName("tự tham gia chỉ tạo yêu cầu PENDING")
        void tu_tham_gia_chi_la_yeu_cau() {
            Integer groupId = group(GroupType.PRIVATE);

            actAs(outsider);
            assertThat(groupService.join(groupId).status()).isEqualTo(GroupMemberStatus.PENDING);
            assertThat(groupService.getListJoined(outsider))
                    .extracting(GroupDtos.GroupSummary::id).doesNotContain(groupId);
        }

        /**
         * Nhánh D của {@code PostVisibility}. Bỏ điều kiện {@code status = ACTIVE} ở đó thì chỉ cần
         * bấm xin vào là đọc được sạch bài trong nhóm trước khi ai kịp duyệt.
         */
        @Test
        @DisplayName("đang chờ duyệt thì KHÔNG thấy bài nào trong nhóm")
        void khong_thay_bai_khi_dang_cho() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);
            Integer postId = groupPost(groupId, member);

            actAs(outsider);
            groupService.join(groupId);

            actAs(outsider);
            assertThatThrownBy(() -> postService.getOneById(postId, outsider))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("đang chờ duyệt thì không bị đếm vào danh sách thành viên")
        void khong_dem_vao_danh_sach() {
            Integer groupId = group(GroupType.PRIVATE);
            int before = detailAs(groupId, owner).totalMembers();

            actAs(outsider);
            groupService.join(groupId);

            GroupDtos.GroupDetail detail = detailAs(groupId, owner);
            assertThat(detail.totalMembers()).isEqualTo(before);
            assertThat(detail.members())
                    .extracting(GroupDtos.GroupMemberView::accountId).doesNotContain(outsider);
        }

        @Test
        @DisplayName("được duyệt thì thành viên thật, thấy bài và đăng được")
        void duyet_thi_thanh_thanh_vien() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);
            Integer postId = groupPost(groupId, member);

            actAs(outsider);
            groupService.join(groupId);
            actAs(owner);
            groupService.reviewJoinRequest(groupId, outsider, true);

            assertThat(statusOf(groupId, outsider)).isEqualTo(GroupMemberStatus.ACTIVE);
            actAs(outsider);
            assertThat(inTransaction(() -> postService.getOneById(postId, outsider)))
                    .isNotNull();
            assertThat(groupPost(groupId, outsider)).isPositive();
        }

        @Test
        @DisplayName("bị từ chối thì xoá hàng, và xin lại được")
        void tu_choi_thi_xin_lai_duoc() {
            Integer groupId = group(GroupType.PRIVATE);

            actAs(outsider);
            groupService.join(groupId);
            actAs(owner);
            groupService.reviewJoinRequest(groupId, outsider, false);
            assertThat(statusOf(groupId, outsider)).isNull();

            actAs(outsider);
            assertThat(groupService.join(groupId).status()).isEqualTo(GroupMemberStatus.PENDING);
        }

        @Test
        @DisplayName("tự huỷ được yêu cầu của mình")
        void tu_huy_yeu_cau() {
            Integer groupId = group(GroupType.PRIVATE);
            actAs(outsider);
            groupService.join(groupId);

            actAs(outsider);
            groupService.cancelJoinRequest(groupId);
            assertThat(statusOf(groupId, outsider)).isNull();
        }

        @Test
        @DisplayName("chi tiết nhóm bị che với người ngoài nhưng vẫn tìm thấy được")
        void chi_tiet_bi_che_voi_nguoi_ngoai() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);

            GroupDtos.GroupDetail asOutsider = detailAs(groupId, outsider);
            assertThat(asOutsider.restricted()).isTrue();
            assertThat(asOutsider.members()).isEmpty();
            // Metadata vẫn còn: không có nó thì không có đường nào tìm ra nhóm mà xin vào
            assertThat(asOutsider.name()).isNotBlank();
            assertThat(asOutsider.totalMembers()).isPositive();

            GroupDtos.GroupDetail asMember = detailAs(groupId, member);
            assertThat(asMember.restricted()).isFalse();
            assertThat(asMember.members()).isNotEmpty();
        }

        @Test
        @DisplayName("khách chưa đăng nhập cũng bị che")
        void khach_cung_bi_che() {
            Integer groupId = group(GroupType.PRIVATE);
            GroupDtos.GroupDetail detail = detailAs(groupId, null);

            assertThat(detail.restricted()).isTrue();
            assertThat(detail.viewerStatus()).isEqualTo(GroupDtos.ViewerStatus.NONE);
        }

        @Test
        @DisplayName("viewerStatus phản ánh đúng ba trạng thái")
        void viewer_status_dung() {
            Integer groupId = group(GroupType.PRIVATE);
            assertThat(detailAs(groupId, outsider).viewerStatus())
                    .isEqualTo(GroupDtos.ViewerStatus.NONE);

            actAs(outsider);
            groupService.join(groupId);
            assertThat(detailAs(groupId, outsider).viewerStatus())
                    .isEqualTo(GroupDtos.ViewerStatus.PENDING_REQUEST);

            actAs(owner);
            groupService.reviewJoinRequest(groupId, outsider, true);
            assertThat(detailAs(groupId, outsider).viewerStatus())
                    .isEqualTo(GroupDtos.ViewerStatus.MEMBER);
        }
    }

    @Nested
    @DisplayName("Ai được duyệt yêu cầu")
    class QuyenDuyet {

        @Test
        @DisplayName("thành viên thường thì không, ADMIN của nhóm thì được")
        void chi_quan_tri_duyet_duoc() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);

            actAs(outsider);
            groupService.join(groupId);

            actAs(member);
            assertThatThrownBy(() -> groupService.reviewJoinRequest(groupId, outsider, true))
                    .isInstanceOf(ForbiddenException.class);

            actAs(admin);
            assertThat(groupService.reviewJoinRequest(groupId, outsider, true).status())
                    .isEqualTo(GroupMemberStatus.ACTIVE);
        }

        @Test
        @DisplayName("hộp thư yêu cầu chỉ chứa yêu cầu tự gửi, không chứa lời mời")
        void hop_thu_khong_lan_loi_moi() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);

            actAs(outsider);
            groupService.join(groupId);
            // member mời invitee — hàng PENDING này KHÔNG phải việc của quản trị nhóm
            actAs(member);
            groupService.invite(groupId, invitee);

            actAs(owner);
            assertThat(inTransaction(() -> groupService.listJoinRequests(groupId)))
                    .extracting(GroupDtos.PendingMemberView::accountId)
                    .containsExactly(outsider);
        }

        @Test
        @DisplayName("quản trị nhóm không duyệt được một lời mời")
        void khong_duyet_duoc_loi_moi() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);
            actAs(member);
            groupService.invite(groupId, invitee);

            actAs(owner);
            assertThatThrownBy(() -> groupService.reviewJoinRequest(groupId, invitee, true))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Lời mời: chính người được mời trả lời")
    class LoiMoi {

        @Test
        @DisplayName("thành viên thường mời được, và lời mời chưa cấp quyền gì")
        void thanh_vien_moi_duoc() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);
            Integer postId = groupPost(groupId, member);

            actAs(member);
            assertThat(groupService.invite(groupId, invitee).status())
                    .isEqualTo(GroupMemberStatus.PENDING);

            actAs(invitee);
            assertThatThrownBy(() -> postService.getOneById(postId, invitee))
                    .isInstanceOf(NotFoundException.class);
            assertThat(groupService.getListJoined(invitee))
                    .extracting(GroupDtos.GroupSummary::id).doesNotContain(groupId);
        }

        @Test
        @DisplayName("người ngoài nhóm không mời được")
        void nguoi_ngoai_khong_moi_duoc() {
            Integer groupId = group(GroupType.PUBLIC);

            actAs(outsider);
            assertThatThrownBy(() -> groupService.invite(groupId, invitee))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("chấp nhận thì thành thành viên và thấy bài")
        void chap_nhan_thi_thanh_thanh_vien() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);
            Integer postId = groupPost(groupId, member);

            actAs(member);
            groupService.invite(groupId, invitee);

            actAs(invitee);
            assertThat(groupService.respondToInvite(groupId, true).status())
                    .isEqualTo(GroupMemberStatus.ACTIVE);
            assertThat(inTransaction(() -> postService.getOneById(postId, invitee)))
                    .isNotNull();
        }

        @Test
        @DisplayName("từ chối thì xoá hàng, mời lại được")
        void tu_choi_thi_moi_lai_duoc() {
            Integer groupId = group(GroupType.PUBLIC);
            joinAsMember(groupId);

            actAs(member);
            groupService.invite(groupId, invitee);
            actAs(invitee);
            groupService.respondToInvite(groupId, false);
            assertThat(statusOf(groupId, invitee)).isNull();

            actAs(member);
            assertThat(groupService.invite(groupId, invitee).status())
                    .isEqualTo(GroupMemberStatus.PENDING);
        }

        @Test
        @DisplayName("hộp thư lời mời chỉ của tài khoản đang thao tác")
        void hop_thu_loi_moi() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);
            actAs(member);
            groupService.invite(groupId, invitee);

            actAs(invitee);
            assertThat(inTransaction(() -> groupService.listMyInvites()))
                    .extracting(GroupDtos.PendingInviteView::groupId).contains(groupId);

            actAs(outsider);
            assertThat(inTransaction(() -> groupService.listMyInvites())).isEmpty();
        }

        /** Bấm "tham gia" khi đang có lời mời = chấp nhận lời mời đó; kết quả người dùng muốn là như nhau */
        @Test
        @DisplayName("tham gia khi đang được mời thì coi như chấp nhận")
        void tham_gia_khi_duoc_moi() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);
            actAs(member);
            groupService.invite(groupId, invitee);

            actAs(invitee);
            assertThat(groupService.join(groupId).status()).isEqualTo(GroupMemberStatus.ACTIVE);
        }

        @Test
        @DisplayName("không mời được người đã ở trong nhóm")
        void khong_moi_lai_thanh_vien() {
            Integer groupId = group(GroupType.PUBLIC);
            joinAsMember(groupId);

            actAs(member);
            assertThatThrownBy(() -> groupService.invite(groupId, owner))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    @DisplayName("Rời nhóm")
    class RoiNhom {

        @Test
        @DisplayName("thành viên rời được, và mất quyền xem bài trong nhóm PRIVATE")
        void thanh_vien_roi_duoc() {
            Integer groupId = group(GroupType.PRIVATE);
            joinAsMember(groupId);
            Integer postId = groupPost(groupId, owner);

            actAs(member);
            assertThat(inTransaction(() -> postService.getOneById(postId, member))).isNotNull();

            actAs(member);
            groupService.leave(groupId);

            assertThat(statusOf(groupId, member)).isNull();
            actAs(member);
            assertThatThrownBy(() -> postService.getOneById(postId, member))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("chủ nhóm không rời được — phải xoá nhóm")
        void chu_nhom_khong_roi_duoc() {
            Integer groupId = group(GroupType.PUBLIC);

            actAs(owner);
            assertThatThrownBy(() -> groupService.leave(groupId))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("người ngoài nhóm rời nhóm thì báo lỗi")
        void nguoi_ngoai_khong_roi_duoc() {
            Integer groupId = group(GroupType.PUBLIC);

            actAs(outsider);
            assertThatThrownBy(() -> groupService.leave(groupId))
                    .isInstanceOf(BadRequestException.class);
        }
    }
}

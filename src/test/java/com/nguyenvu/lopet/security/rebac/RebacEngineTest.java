package com.nguyenvu.lopet.security.rebac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.support.AuthTestSupport;

@ExtendWith(MockitoExtension.class)
@DisplayName("RebacEngine — thứ tự quyết định")
class RebacEngineTest {

    private static final Integer POST_ID = 42;

    @Mock
    private RelationResolver platformResolver;
    @Mock
    private RelationResolver postResolver;

    @AfterEach
    void clearIdentity() {
        AuthTestSupport.clear();
    }

    private RebacEngine engine() {
        when(platformResolver.type()).thenReturn(ObjectRef.PLATFORM);
        when(postResolver.type()).thenReturn(ObjectRef.POST);
        return new RebacEngine(List.of(platformResolver, postResolver));
    }

    private void platformGives(Relation... relations) {
        when(platformResolver.relationsOf(eq(null), any())).thenReturn(Optional.of(Set.of(relations)));
    }

    @Test
    @DisplayName("khách chưa đăng nhập bị chặn ở bước 1, chưa chạm tới resolver nào")
    void khach_bi_chan_truoc_khi_nap() {
        RebacEngine engine = engine();

        assertThatThrownBy(() -> engine.require(RebacModel.POST_DELETE, ObjectRef.post(POST_ID)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Chưa xác thực");

        verify(postResolver, never()).relationsOf(any(), any());
    }

    @Test
    @DisplayName("MODERATOR được cho qua TRƯỚC khi nạp bài — nếu không sẽ nhận 404 cho bài PRIVATE")
    void moderator_bypass_truoc_khi_nap_tai_nguyen() {
        RebacEngine engine = engine();
        AuthTestSupport.actAs(7, List.of("MODERATOR"));
        platformGives(Relation.AUTHENTICATED, Relation.MODERATOR);

        engine.require(RebacModel.POST_DELETE, ObjectRef.post(POST_ID));

        verify(postResolver, never()).relationsOf(any(), any());
    }

    @Test
    @DisplayName("MODERATOR vẫn không sửa được bài người khác")
    void moderator_khong_bypass_duoc_quyen_sua() {
        RebacEngine engine = engine();
        AuthTestSupport.actAs(7, List.of("MODERATOR"));
        platformGives(Relation.AUTHENTICATED, Relation.MODERATOR);
        when(postResolver.relationsOf(eq(POST_ID), any())).thenReturn(Optional.of(Set.of(Relation.VIEWER)));

        assertThatThrownBy(() -> engine.require(RebacModel.POST_UPDATE, ObjectRef.post(POST_ID)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không sở hữu tài nguyên này");
    }

    @Test
    @DisplayName("ADMIN KHÔNG đọc được tin nhắn riêng — message:read không có nhánh nền tảng")
    void admin_khong_bypass_duoc_tin_nhan() {
        when(platformResolver.type()).thenReturn(ObjectRef.PLATFORM);
        RelationResolver messageResolver = org.mockito.Mockito.mock(RelationResolver.class);
        when(messageResolver.type()).thenReturn(ObjectRef.MESSAGE);
        RebacEngine engine = new RebacEngine(List.of(platformResolver, messageResolver));

        AuthTestSupport.actAs(7, List.of("ADMIN"));
        platformGives(Relation.AUTHENTICATED, Relation.ADMIN);
        when(messageResolver.relationsOf(eq(9), any())).thenReturn(Optional.of(Set.of()));

        assertThatThrownBy(() -> engine.require(RebacModel.MESSAGE_READ, ObjectRef.message(9)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("không nạp được tài nguyên thì 404, không phải 403 — chống dò id")
    void khong_nap_duoc_thi_404() {
        RebacEngine engine = engine();
        AuthTestSupport.actAs(7);
        platformGives(Relation.AUTHENTICATED);
        when(postResolver.relationsOf(eq(POST_ID), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.require(RebacModel.POST_DELETE, ObjectRef.post(POST_ID)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy tài nguyên");
    }

    @Test
    @DisplayName("nạp được nhưng không phải chủ thì 403")
    void nap_duoc_nhung_khong_phai_chu_thi_403() {
        RebacEngine engine = engine();
        AuthTestSupport.actAs(7);
        platformGives(Relation.AUTHENTICATED);
        when(postResolver.relationsOf(eq(POST_ID), any())).thenReturn(Optional.of(Set.of(Relation.VIEWER)));

        assertThatThrownBy(() -> engine.require(RebacModel.POST_DELETE, ObjectRef.post(POST_ID)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không sở hữu tài nguyên này");
    }

    @Test
    @DisplayName("chủ bài đi qua được")
    void chu_bai_di_qua_duoc() {
        RebacEngine engine = engine();
        AuthTestSupport.actAs(7);
        platformGives(Relation.AUTHENTICATED);
        when(postResolver.relationsOf(eq(POST_ID), any()))
                .thenReturn(Optional.of(Set.of(Relation.VIEWER, Relation.OWNER)));

        engine.require(RebacModel.POST_DELETE, ObjectRef.post(POST_ID));
        assertThat(engine.check(RebacModel.POST_DELETE, ObjectRef.post(POST_ID))).isTrue();
    }

    @Test
    @DisplayName("action đích platform: đã đăng nhập là đủ, và không nạp tài nguyên nào")
    void action_dich_platform() {
        RebacEngine engine = engine();
        AuthTestSupport.actAs(7);
        platformGives(Relation.AUTHENTICATED);

        engine.require(RebacModel.POST_CREATE, ObjectRef.platform());

        verify(postResolver, never()).relationsOf(any(), any());
    }

    @Test
    @DisplayName("check() nuốt lỗi thành false thay vì ném")
    void check_tra_ve_false_thay_vi_nem() {
        RebacEngine engine = engine();
        AuthTestSupport.actAs(7);
        platformGives(Relation.AUTHENTICATED);
        when(postResolver.relationsOf(eq(POST_ID), any())).thenReturn(Optional.empty());

        assertThat(engine.check(RebacModel.POST_DELETE, ObjectRef.post(POST_ID))).isFalse();
    }

    @Test
    @DisplayName("hai resolver cùng type là lỗi cấu hình, phát hiện ngay lúc dựng bean")
    void hai_resolver_trung_type_bi_tu_choi() {
        when(platformResolver.type()).thenReturn(ObjectRef.POST);
        when(postResolver.type()).thenReturn(ObjectRef.POST);

        assertThatThrownBy(() -> new RebacEngine(List.of(platformResolver, postResolver)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ObjectRef.POST);
    }

    @Test
    @DisplayName("chưa có resolver cho loại đối tượng thì ném, không âm thầm cho qua")
    void thieu_resolver_thi_nem() {
        RebacEngine engine = engine();
        AuthTestSupport.actAs(7);
        platformGives(Relation.AUTHENTICATED);

        assertThatThrownBy(() -> engine.require(RebacModel.POST_DELETE, ObjectRef.account(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ObjectRef.ACCOUNT);
    }

    @Test
    @DisplayName("danh tính lấy từ SecurityContext, không nhận qua tham số")
    void danh_tinh_lay_tu_security_context() {
        RebacEngine engine = engine();
        AuthTestSupport.actAs(7, List.of("MODERATOR"));
        platformGives(Relation.AUTHENTICATED, Relation.MODERATOR);

        engine.require(RebacModel.POST_DELETE, ObjectRef.post(POST_ID));

        org.mockito.ArgumentCaptor<UserPrincipal> caller =
                org.mockito.ArgumentCaptor.forClass(UserPrincipal.class);
        verify(platformResolver).relationsOf(eq(null), caller.capture());
        assertThat(caller.getValue().id()).isEqualTo(7);
        assertThat(caller.getValue().roles()).containsExactly("MODERATOR");
    }
}

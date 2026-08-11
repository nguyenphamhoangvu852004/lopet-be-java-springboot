package com.nguyenvu.lopet.security.authz;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.role.entity.RoleName;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

/**
 * Tầng 2 của phân quyền: "được phép làm lên ĐÚNG tài nguyên này không?" — bản dịch của
 * {@code requireOwnership()}.
 *
 * <p>Đây chính là tầng mà RBAC phẳng không diễn tả được. Trong mạng xã hội phần lớn quyền không phụ
 * thuộc "bạn là ai" mà phụ thuộc "bạn là gì đối với tài nguyên này": chủ post, chủ nhóm, chủ quảng cáo.
 *
 * <p>Hai chi tiết bắt buộc phải giữ đúng thứ tự:
 * <ul>
 *   <li>bypassRoles được xét TRƯỚC khi gọi {@code loader} — nhờ vậy staff kiểm duyệt vẫn xoá được
 *       bài/bình luận trong nội dung riêng tư mà bộ lọc quyền xem sẽ trả về rỗng;</li>
 *   <li>danh tính so sánh LUÔN lấy từ token, không bao giờ từ body/params.</li>
 * </ul>
 */
public final class OwnershipGuard {

    /** Mặc định của TS: chỉ ADMIN được bỏ qua kiểm tra sở hữu */
    public static final Set<RoleName> DEFAULT_BYPASS = Set.of(RoleName.ADMIN);
    /** Dùng cho tài nguyên mà không role nào được bỏ qua (tin nhắn, hồ sơ cá nhân, sửa bài) */
    public static final Set<RoleName> NO_BYPASS = Set.of();

    /**
     * @param loader   nạp tài nguyên; trả {@code null} nghĩa là không tồn tại HOẶC người gọi không
     *                 được phép nhìn thấy nó — hai trường hợp cố ý không phân biệt được
     * @param ownersOf các tài khoản được phép đụng vào tài nguyên (tin nhắn có hai bên hợp lệ)
     * @return tài nguyên đã nạp, hoặc {@code null} khi người gọi đi qua bằng bypass role
     */
    public static <T> T check(Supplier<T> loader, Function<T, Collection<Integer>> ownersOf, Set<RoleName> bypassRoles) {
        UserPrincipal caller = CurrentUser.optional();
        if (caller == null) {
            throw new ForbiddenException("Chưa xác thực");
        }

        boolean bypassed = caller.roles().stream()
                .anyMatch(role -> bypassRoles.stream().anyMatch(bypass -> bypass.name().equals(role)));
        if (bypassed) {
            return null;
        }

        T resource = loader.get();
        if (resource == null) {
            throw new NotFoundException("Không tìm thấy tài nguyên");
        }

        Set<Integer> allowed = ownersOf.apply(resource).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!allowed.contains(caller.id())) {
            throw new ForbiddenException("Bạn không sở hữu tài nguyên này");
        }
        return resource;
    }

    /** Dạng rút gọn cho tài nguyên chỉ có đúng một chủ sở hữu */
    public static <T> T checkSingleOwner(Supplier<T> loader, Function<T, Integer> ownerOf, Set<RoleName> bypassRoles) {
        return check(loader, resource -> List.of(ownerOf.apply(resource)).stream().filter(Objects::nonNull).toList(),
                bypassRoles);
    }

    public static Collection<Integer> owners(Integer... ids) {
        return Arrays.stream(ids).filter(Objects::nonNull).toList();
    }

    private OwnershipGuard() {
    }
}

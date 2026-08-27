package com.nguyenvu.lopet.accountprofile.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;

/**
 * Mọi hàm tra bằng {@code accountId} chứ không phải id hồ sơ: không luồng nào nhận id hồ sơ từ
 * client, nên không tồn tại tham số để trỏ sang hồ sơ của người khác.
 *
 * <p>{@link #findByAccountId} KHÔNG lọc quyền xem và chỉ dành cho đường đọc hồ sơ CỦA CHÍNH MÌNH
 * (và cho các module nhúng hồ sơ vào nội dung mà người xem đã có quyền xem). Đường đọc hồ sơ
 * NGƯỜI KHÁC phải dùng {@link #findVisibleByAccountId}.
 */
public interface AccountProfileRepository extends JpaRepository<AccountProfile, Integer> {

    @Query("select p from AccountProfile p where p.account.id = :accountId")
    Optional<AccountProfile> findByAccountId(@Param("accountId") Integer accountId);

    /**
     * Hồ sơ của một người khác, đã lọc theo {@link AccountProfileVisibilityFilter}. Trả rỗng vừa
     * nghĩa "không có hồ sơ" vừa nghĩa "không được xem" — người gọi phải quy cả hai về 404, nếu
     * không endpoint thành công cụ dò xem một tài khoản có tồn tại hay không.
     */
    @Query("select p from AccountProfile p left join p.account a "
            + " where a.id = :accountId and " + AccountProfileVisibilityFilter.VISIBLE_TO)
    Optional<AccountProfile> findVisibleByAccountId(@Param("accountId") Integer accountId,
                                                    @Param("viewerId") Integer viewerId);
}

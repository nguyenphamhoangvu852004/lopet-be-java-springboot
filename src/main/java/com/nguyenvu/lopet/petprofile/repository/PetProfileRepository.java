package com.nguyenvu.lopet.petprofile.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.petprofile.entity.PetProfile;

/**
 * {@code findById} kế thừa từ {@link JpaRepository} đã tự loại hồ sơ ngừng hoạt động nhờ
 * {@code @SQLRestriction} trên entity, nhưng KHÔNG lọc quyền xem — vì vậy mọi luồng đọc do người
 * dùng kích hoạt phải đi qua {@link #findVisibleByHandle} hoặc {@link #findVisibleByPetId}.
 */
public interface PetProfileRepository extends JpaRepository<PetProfile, Integer> {

    /** Dùng cho luồng ghi (chủ sở hữu tự sửa), không lọc quyền xem — guard đã kiểm sở hữu trước đó */
    Optional<PetProfile> findByPetId(Integer petId);

    /**
     * Hồ sơ ĐẠI DIỆN của một tài khoản — thú cưng có id nhỏ nhất, tức con đầu tiên người dùng tạo.
     *
     * <p>Chỉ dùng ở những chỗ mà chủ thể hiển thị vẫn là TÀI KHOẢN nhưng giao diện cần một khuôn mặt
     * để hiện: thông báo (actor/receptor là account vì thông báo gửi tới con người, không tới con
     * vật). Không dùng cho nội dung xã hội — bài viết, bình luận, thành viên nhóm đều biết chính xác
     * pet nào là tác giả nên phải lấy hồ sơ của đúng con đó.
     *
     * <p>Quy tắc "id nhỏ nhất" là một lựa chọn tuỳ ý và có thể sai với người dùng nhiều thú cưng.
     * Khi mô hình có khái niệm "pet đại diện" tường minh (một cột trên {@code accounts}), thay truy
     * vấn này là đủ — không nơi gọi nào phải sửa.
     */
    @Query("select pp from PetProfile pp join pp.pet p where p.account.id = :accountId order by p.id asc limit 1")
    Optional<PetProfile> findRepresentativeByAccountId(@Param("accountId") Integer accountId);

    /**
     * Kiểm trùng handle. Không dùng {@code findByHandle().isPresent()}: hồ sơ đã ngừng hoạt động bị
     * {@code @SQLRestriction} loại khỏi kết quả nhưng hàng vẫn nằm trong bảng, nên UNIQUE index vẫn
     * chặn — kiểm bằng câu truy vấn native để lỗi hiện ra ở tầng nghiệp vụ (409) thay vì thành
     * DataIntegrityViolationException (500).
     *
     * <p>Trả {@code long} chứ không phải {@code boolean}: MySQL trả kết quả của {@code count(*) > 0}
     * dưới dạng BIGINT, và Spring Data không ép được Long sang Boolean — mọi lời gọi sẽ ném
     * ClassCastException ở runtime chứ không phải lỗi lúc khởi động.
     */
    @Query(value = "select count(*) from pet_profiles where handle = :handle", nativeQuery = true)
    long countByHandleIncludingDeactivated(@Param("handle") String handle);

    @Query("select pp from PetProfile pp join fetch pp.pet p where pp.handle = :handle and "
            + PetProfileVisibilityFilter.VISIBLE_TO)
    Optional<PetProfile> findVisibleByHandle(@Param("handle") String handle,
                                             @Param("viewerId") Integer viewerId);

    @Query("select pp from PetProfile pp join fetch pp.pet p where p.id = :petId and "
            + PetProfileVisibilityFilter.VISIBLE_TO)
    Optional<PetProfile> findVisibleByPetId(@Param("petId") Integer petId,
                                            @Param("viewerId") Integer viewerId);
}

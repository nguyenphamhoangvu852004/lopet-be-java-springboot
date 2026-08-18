package com.nguyenvu.lopet.pet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.petprofile.repository.PetProfileVisibilityFilter;

/**
 * {@code findById} kế thừa từ {@link JpaRepository} đã tự loại thú cưng đã ngừng hoạt động nhờ
 * {@code @SQLRestriction} trên entity, nhưng KHÔNG lọc quyền xem — vì vậy mọi luồng đọc do người
 * dùng kích hoạt phải đi qua {@link #findVisibleById}.
 *
 * <p>Điều kiện quyền xem dùng chung {@link PetProfileVisibilityFilter} với module hồ sơ: phạm vi
 * riêng tư là thuộc tính của HỒ SƠ, nên hai module không được có hai câu trả lời khác nhau cho cùng
 * một câu hỏi.
 */
public interface PetRepository extends JpaRepository<Pet, Integer> {

    @Query("select p from Pet p join p.petProfile pp where p.id = :id and "
            + PetProfileVisibilityFilter.VISIBLE_TO)
    Optional<Pet> findVisibleById(@Param("id") Integer id, @Param("viewerId") Integer viewerId);

    /**
     * Thú cưng của một tài khoản.
     *
     * <p>{@code ownerId} LUÔN là id lấy từ token ở tầng controller, không bao giờ từ query param —
     * đó là điều kiện để endpoint "thú cưng của tôi" không trở thành công cụ liệt kê hồ sơ riêng tư
     * của người khác.
     */
    @Query("""
            select p from Pet p
            join fetch p.petProfile
            where p.account.id = :ownerId
            order by p.createdAt desc
            """)
    List<Pet> findAllByAccountId(@Param("ownerId") Integer ownerId);

    /**
     * Nguồn sự thật cho việc validate header {@code X-Pet-Id} và cho cache Redis
     * {@code pet:owner:<petId>} — xem {@code PetOwnerResolver}.
     *
     * <p>Trả {@code Optional<Integer>} chứ không nạp cả entity: câu hỏi ở mỗi request tương tác chỉ
     * là "con này của ai", nạp thêm gì cũng là lãng phí.
     */
    @Query("select p.account.id from Pet p where p.id = :petId")
    Optional<Integer> findOwnerAccountId(@Param("petId") Integer petId);

    boolean existsByAccountId(Integer accountId);
}

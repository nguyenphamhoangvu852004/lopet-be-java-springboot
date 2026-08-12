package com.nguyenvu.lopet.pet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.pet.entity.Pet;

/**
 * {@code findById} kế thừa từ {@link JpaRepository} đã tự loại hồ sơ đã xoá mềm nhờ
 * {@code @SQLRestriction} trên entity, nhưng KHÔNG lọc quyền xem — vì vậy mọi luồng đọc do người
 * dùng kích hoạt phải đi qua {@link #findVisibleById}.
 */
public interface PetRepository extends JpaRepository<Pet, Integer> {

    @Query("select p from Pet p where p.id = :id and " + PetVisibilityFilter.VISIBLE_TO)
    Optional<Pet> findVisibleById(@Param("id") Integer id, @Param("viewerId") Integer viewerId);

    /**
     * Danh sách thú cưng của một tài khoản, gồm cả vai trò CO_OWNER.
     *
     * <p>{@code ownerId} LUÔN là id lấy từ token ở tầng controller, không bao giờ từ query param —
     * đó là điều kiện để endpoint "thú cưng của tôi" không trở thành công cụ liệt kê hồ sơ riêng tư
     * của người khác.
     */
    @Query("""
            select p from Pet p
            where exists (select 1 from PetOwnership po where po.petId = p.id and po.userId = :ownerId)
            order by p.createdAt desc
            """)
    List<Pet> findAllOwnedBy(@Param("ownerId") Integer ownerId);
}

package com.nguyenvu.lopet.pet.entity;

/**
 * Vòng đời của con vật. Không có giá trị "DELETED": xoá là xoá MỀM
 * ({@code status = DEACTIVATED} + {@code deletedAt}), vì bài viết và bình luận của người khác vẫn
 * đang trỏ tới {@code pets.id}.
 */
public enum PetStatus {
    ACTIVE,
    DEACTIVATED
}

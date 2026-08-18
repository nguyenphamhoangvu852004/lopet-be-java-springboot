package com.nguyenvu.lopet.petprofile.entity;

/**
 * Trạng thái hồ sơ công khai. Đi SONG SONG với {@code PetStatus} chứ không thay thế nó: một con vật
 * còn ACTIVE vẫn có thể tạm ẩn hồ sơ mạng xã hội của mình, và ngược lại thì không — ngừng hoạt động
 * con vật thì hồ sơ cũng phải tắt theo (xem {@code PetService.deactivate}).
 */
public enum PetProfileStatus {
    ACTIVE,
    DEACTIVATED
}

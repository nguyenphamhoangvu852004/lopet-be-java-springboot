package com.nguyenvu.lopet.pet.entity;

/**
 * Schema chỉ khai {@code gender varchar(20) NOT NULL} mà không liệt kê giá trị hợp lệ (khác với
 * status/visibility/ownership_type đều có note). Bộ ba dưới đây là quy ước tạm — cột là varchar
 * nên bổ sung giá trị về sau không cần đổi DDL.
 */
public enum PetGender {
    MALE,
    FEMALE,
    UNKNOWN
}

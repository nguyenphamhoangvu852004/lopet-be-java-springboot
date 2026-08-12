package com.nguyenvu.lopet.pet.entity;

/**
 * Loài được hỗ trợ. Cột {@code species varchar(30)} nên bổ sung giá trị về sau không cần đổi DDL —
 * {@code OTHER} là van xả để người dùng không bị chặn khi nuôi loài chưa có trong danh sách.
 */
public enum PetSpecies {
    DOG,
    CAT,
    BIRD,
    RABBIT,
    HAMSTER,
    FISH,
    REPTILE,
    OTHER
}

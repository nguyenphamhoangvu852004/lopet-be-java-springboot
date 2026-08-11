package com.nguyenvu.lopet.group.entity;

/**
 * Resource role — vai trò của một tài khoản BÊN TRONG một group cụ thể.
 *
 * <p>Đây là thứ RBAC toàn cục không diễn tả được: quyền kick member hay sửa nhóm không phụ thuộc
 * "bạn là ai" mà phụ thuộc "bạn là gì đối với group này".
 */
public enum GroupMemberRole {
    OWNER,
    ADMIN,
    MEMBER
}

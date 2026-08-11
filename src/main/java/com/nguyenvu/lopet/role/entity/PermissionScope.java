package com.nguyenvu.lopet.role.entity;

public enum PermissionScope {
    /** Được thực hiện hành động trên mọi tài nguyên */
    ANY,
    /** Chỉ được thực hiện trên tài nguyên do chính mình sở hữu — còn phải qua tầng ownership */
    OWN
}

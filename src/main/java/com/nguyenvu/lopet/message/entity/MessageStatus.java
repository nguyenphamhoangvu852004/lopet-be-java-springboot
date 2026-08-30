package com.nguyenvu.lopet.message.entity;

public enum MessageStatus {
    SENT,
    DELIVERED,
    READ;

    public boolean isAtLeast(MessageStatus other) {
        return ordinal() >= other.ordinal();
    }
}

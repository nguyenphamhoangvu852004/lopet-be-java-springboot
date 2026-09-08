package com.nguyenvu.lopet.post;

import com.nguyenvu.lopet.common.exception.BadRequestException;

public enum ReactAction {
    LIKE,
    UNLIKE;

    public static ReactAction from(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Missing action, expected one of: like, unlike");
        }
        try {
            return ReactAction.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid action: " + value + ", expected one of: like, unlike");
        }
    }
}

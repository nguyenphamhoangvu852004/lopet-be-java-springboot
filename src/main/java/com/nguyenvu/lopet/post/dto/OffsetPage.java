package com.nguyenvu.lopet.post.dto;

import java.util.List;

public record OffsetPage<T>(
        List<T> content,
        int page,
        int limit,
        long totalItems,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static <T> OffsetPage<T> of(List<T> content, int page, int limit, long totalItems) {
        int totalPages = (int) ((totalItems + limit - 1) / limit);
        return new OffsetPage<>(content, page, limit, totalItems, totalPages, page < totalPages, page > 1);
    }
}

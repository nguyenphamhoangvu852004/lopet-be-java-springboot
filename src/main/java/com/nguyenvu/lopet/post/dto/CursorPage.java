package com.nguyenvu.lopet.post.dto;

import java.util.List;

public record CursorPage<T>(
        List<T> content,
        Integer nextCursor,
        boolean hasNext
) {}

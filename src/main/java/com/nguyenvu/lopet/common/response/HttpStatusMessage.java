package com.nguyenvu.lopet.common.response;

/**
 * Bản sao của {@code src/global/httpStatusCode.ts}. Các chuỗi này đi thẳng ra body response nên
 * phải giữ nguyên văn, kể cả khoảng trắng ("NOT FOUND" chứ không phải "NOT_FOUND").
 */
public final class HttpStatusMessage {

    public static final String OK = "OK";
    public static final String CREATED = "CREATED";
    public static final String NO_CONTENT = "NO CONTENT";
    public static final String BAD_REQUEST = "BAD REQUEST";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL SERVER ERROR";

    private HttpStatusMessage() {
    }
}
